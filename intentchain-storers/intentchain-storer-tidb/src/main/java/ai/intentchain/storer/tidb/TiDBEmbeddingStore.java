package ai.intentchain.storer.tidb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TiDB 向量存储实现
 */
@Slf4j
public class TiDBEmbeddingStore implements EmbeddingStore<TextSegment>, AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TiDBMetadataFilterMapper FILTER_MAPPER = new TiDBMetadataFilterMapper();

    // SQL 语句模板
    private static final String CREATE_TABLE_TEMPLATE = """
            CREATE TABLE IF NOT EXISTS %s (
                id VARCHAR(36) PRIMARY KEY,
                embedding VECTOR(%d) COMMENT 'hnsw(distance=%s)',
                text TEXT,
                metadata JSON
            )
            """;

    private static final String ALTER_TABLE_TIFLASH_TEMPLATE = """
            ALTER TABLE %s SET TIFLASH REPLICA 1
            """;

    private static final String CREATE_VECTOR_INDEX_TEMPLATE = """
            ALTER TABLE %s ADD VECTOR INDEX %s ((%s(embedding)))
            """;

    private static final String DROP_TABLE_TEMPLATE = """
            DROP TABLE IF EXISTS %s
            """;

    private static final String INSERT_TEMPLATE = """
            INSERT INTO %s (id, embedding, text, metadata) VALUES (?, ?, ?, ?)
            """;

    private static final String SEARCH_QUERY_TEMPLATE = """
            SELECT id, embedding, text, metadata,
                   %s(embedding, ?) AS distance
            FROM %s
            ORDER BY distance
            LIMIT ?
            """;

    private static final String DELETE_BY_IDS_TEMPLATE = """
            DELETE FROM %s WHERE id = ?
            """;

    private static final String DELETE_BY_FILTER_TEMPLATE = """
            DELETE FROM %s WHERE %s
            """;

    private static final String TRUNCATE_QUERY_TEMPLATE = """
            TRUNCATE TABLE %s
            """;

    private final DataSource dataSource;
    private final String tableName;
    private final Integer dimension;
    private final DistanceMetric distanceMetric;

    @Builder
    public TiDBEmbeddingStore(String jdbcUrl,
                              String host,
                              Integer port,
                              String database,
                              String username,
                              String password,
                              @NonNull String tableName,
                              @NonNull Integer dimension,
                              @NonNull DistanceMetric distanceMetric,
                              Boolean createTable,
                              Boolean dropTableFirst,
                              Integer maxPoolSize,
                              Integer minIdleConnections,
                              Long connectionTimeout,
                              Long idleTimeout,
                              Long maxLifetime,
                              Long leakDetectionThreshold,
                              Boolean createVectorIndex) {
        this.tableName = tableName;
        this.dimension = dimension;
        this.distanceMetric = distanceMetric;

        // 初始化数据源
        HikariConfig config = new HikariConfig();
        if (jdbcUrl == null) {
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                    host, port, database));
        } else {
            config.setJdbcUrl(jdbcUrl);
        }
        if (username != null) {
            config.setUsername(username);
        }
        if (password != null) {
            config.setPassword(password);
        }
        config.setMaximumPoolSize(Optional.ofNullable(maxPoolSize).orElse(10));
        config.setMinimumIdle(Optional.ofNullable(minIdleConnections).orElse(2));
        config.setConnectionTimeout(Optional.ofNullable(connectionTimeout).orElse(30000L)); // 30s
        config.setIdleTimeout(Optional.ofNullable(idleTimeout).orElse(600000L)); // 10m
        config.setMaxLifetime(Optional.ofNullable(maxLifetime).orElse(1800000L)); // 30m
        config.setLeakDetectionThreshold(Optional.ofNullable(leakDetectionThreshold).orElse(60000L)); // 1m
        this.dataSource = new HikariDataSource(config);

        // 初始化表
        if (Boolean.TRUE.equals(Optional.ofNullable(dropTableFirst).orElse(false))) {
            dropTable();
        }
        if (Boolean.TRUE.equals(Optional.ofNullable(createTable).orElse(true))) {
            createTableIfNotExists();
            if (Boolean.TRUE.equals(Optional.ofNullable(createVectorIndex).orElse(false))) {
                createVectorIndex();
            }
        }
    }

    private void createTableIfNotExists() {
        String createTableSql = String.format(CREATE_TABLE_TEMPLATE, tableName, dimension, distanceMetric.getValue());

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            log.info("表 {} 创建成功或已存在", tableName);

            // 创建 TiFlash 副本（如果尚未创建）
            try {
                String alterTableSql = String.format(ALTER_TABLE_TIFLASH_TEMPLATE, tableName);
                stmt.execute(alterTableSql);
                log.info("TiFlash 副本已创建或已存在");
            } catch (SQLException e) {
                log.warn("创建 TiFlash 副本失败（可能已存在）: {}", e.getMessage());
            }
        } catch (SQLException e) {
            throw new RuntimeException("创建表失败", e);
        }
    }

    private void dropTable() {
        String dropTableSql = String.format(DROP_TABLE_TEMPLATE, tableName);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(dropTableSql);
            log.info("表 {} 已删除", tableName);
        } catch (SQLException e) {
            throw new RuntimeException("删除表失败", e);
        }
    }

    private void createVectorIndex() {
        // 检查距离度量是否支持向量索引
        if (!distanceMetric.supportsVectorIndex()) {
            log.warn("距离度量 {} 不支持向量索引，跳过索引创建。支持向量索引的距离度量: COSINE, L2",
                    distanceMetric.getValue());
            return;
        }

        String indexName = String.format("idx_%s_embedding", tableName);
        String createIndexSql = String.format(
                CREATE_VECTOR_INDEX_TEMPLATE,
                tableName, indexName, distanceMetric.getSqlFunction()
        );

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createIndexSql);
            log.info("向量索引 {} 创建成功", indexName);
        } catch (SQLException e) {
            // 索引可能已存在，记录警告而不是抛出异常
            log.warn("创建向量索引失败（可能已存在）: {}", e.getMessage());
        }
    }

    @Override
    public String add(Embedding embedding) {
        return add(embedding, null);
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = UUID.randomUUID().toString();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>(embeddings.size());
        String insertSql = String.format(INSERT_TEMPLATE, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {

            for (Embedding embedding : embeddings) {
                String id = UUID.randomUUID().toString();
                pstmt.setString(1, id);
                pstmt.setString(2, embeddingToString(embedding));
                pstmt.setString(3, null);
                pstmt.setString(4, null);
                pstmt.addBatch();
                ids.add(id);
            }

            pstmt.executeBatch();
            log.debug("批量添加了 {} 个向量", embeddings.size());
        } catch (SQLException e) {
            throw new RuntimeException("批量添加向量失败", e);
        }

        return ids;
    }

    /**
     * 批量添加带 TextSegment 的向量
     */
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (embeddings.size() != textSegments.size()) {
            throw new IllegalArgumentException("embeddings 和 textSegments 的数量必须一致");
        }

        List<String> ids = new ArrayList<>(embeddings.size());
        String insertSql = String.format(INSERT_TEMPLATE, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {

            for (int i = 0; i < embeddings.size(); i++) {
                String id = UUID.randomUUID().toString();
                Embedding embedding = embeddings.get(i);
                TextSegment textSegment = textSegments.get(i);

                pstmt.setString(1, id);
                pstmt.setString(2, embeddingToString(embedding));
                pstmt.setString(3, textSegment != null ? textSegment.text() : null);
                pstmt.setString(4, textSegment != null ? metadataToJson(textSegment.metadata()) : null);
                pstmt.addBatch();
                ids.add(id);
            }

            pstmt.executeBatch();
            log.debug("批量添加了 {} 个向量及文本片段", embeddings.size());
        } catch (SQLException e) {
            throw new RuntimeException("批量添加向量失败", e);
        }

        return ids;
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        String insertSql = String.format(INSERT_TEMPLATE, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {

            pstmt.setString(1, id);
            pstmt.setString(2, embeddingToString(embedding));
            pstmt.setString(3, textSegment != null ? textSegment.text() : null);
            pstmt.setString(4, textSegment != null ? metadataToJson(textSegment.metadata()) : null);

            pstmt.executeUpdate();
            log.debug("向量已添加: id={}", id);
        } catch (SQLException e) {
            throw new RuntimeException("添加向量失败", e);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding referenceEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();

        String distanceFunction = getDistanceFunction();
        String searchSql = String.format(SEARCH_QUERY_TEMPLATE, distanceFunction, tableName);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(searchSql)) {

            pstmt.setString(1, embeddingToString(referenceEmbedding));
            pstmt.setInt(2, maxResults);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String embeddingStr = rs.getString("embedding");
                    String text = rs.getString("text");
                    String metadataJson = rs.getString("metadata");
                    double distance = rs.getDouble("distance");

                    // 将距离转换为相似度分数（0-1之间）
                    double score = convertDistanceToScore(distance);

                    // 过滤掉低于最小分数的结果
                    if (score < minScore) {
                        continue;
                    }

                    // 解析向量
                    float[] vector = parseVector(embeddingStr);
                    Embedding embedding = new Embedding(vector);

                    // 构建 TextSegment
                    TextSegment textSegment = null;
                    if (text != null) {
                        Metadata metadata = parseMetadata(metadataJson);
                        textSegment = TextSegment.from(text, metadata);
                    }

                    matches.add(new EmbeddingMatch<>(score, id, embedding, textSegment));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("搜索向量失败", e);
        }

        return new EmbeddingSearchResult<>(matches);
    }

    private String getDistanceFunction() {
        return distanceMetric.getSqlFunction();
    }

    private double convertDistanceToScore(double distance) {
        return distanceMetric.convertDistanceToScore(distance);
    }

    private String embeddingToString(Embedding embedding) {
        return embedding.vectorAsList().stream().map(Object::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private float[] parseVector(String vectorStr) {
        // 去掉方括号并分割
        String content = vectorStr.substring(1, vectorStr.length() - 1);
        String[] parts = content.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    private String metadataToJson(Metadata metadata) {
        if (metadata == null || metadata.toMap().isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata.toMap());
        } catch (Exception e) {
            log.error("序列化元数据失败", e);
            return null;
        }
    }

    private Metadata parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.trim().isEmpty()) {
            return new Metadata();
        }
        try {
            Map<String, String> map = OBJECT_MAPPER.readValue(
                    metadataJson,
                    new TypeReference<Map<String, String>>() {
                    }
            );
            return Metadata.from(map);
        } catch (Exception e) {
            log.error("解析元数据失败", e);
            return new Metadata();
        }
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ValidationUtils.ensureNotNull(ids, "ids");
        ValidationUtils.ensureNotNull(embeddings, "embeddings");

        if (ids.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    String.format("ids 和 embeddings 的数量必须一致，但 ids.size()=%d, embeddings.size()=%d",
                            ids.size(), embeddings.size())
            );
        }

        if (embedded != null && embedded.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    String.format("embedded 的数量必须与 embeddings 一致，但 embedded.size()=%d, embeddings.size()=%d",
                            embedded.size(), embeddings.size())
            );
        }

        String insertSql = String.format(INSERT_TEMPLATE, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {

            for (int i = 0; i < embeddings.size(); i++) {
                String id = ids.get(i);
                Embedding embedding = embeddings.get(i);
                TextSegment textSegment = (embedded != null && i < embedded.size()) ? embedded.get(i) : null;

                pstmt.setString(1, id);
                pstmt.setString(2, embeddingToString(embedding));
                pstmt.setString(3, textSegment != null ? textSegment.text() : null);
                pstmt.setString(4, textSegment != null ? metadataToJson(textSegment.metadata()) : null);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            log.debug("批量添加了 {} 个向量（使用指定 ID）", embeddings.size());
        } catch (SQLException e) {
            throw new RuntimeException("批量添加向量失败", e);
        }
    }

    /**
     * 批量移除指定 ID 的向量
     */
    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        String deleteSql = String.format(DELETE_BY_IDS_TEMPLATE, tableName);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {

            for (String id : ids) {
                pstmt.setString(1, id);
                pstmt.addBatch();
            }

            int[] deleted = pstmt.executeBatch();
            int totalDeleted = Arrays.stream(deleted).sum();
            log.debug("批量删除了 {} 条记录", totalDeleted);
        } catch (SQLException e) {
            throw new RuntimeException("批量删除向量失败", e);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ValidationUtils.ensureNotNull(filter, "filter");

        try {
            String whereClause = FILTER_MAPPER.map(filter);
            String deleteSql = String.format(DELETE_BY_FILTER_TEMPLATE, tableName, whereClause);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate(deleteSql);
                log.info("根据过滤条件删除了 {} 条记录", deleted);
            } catch (SQLException e) {
                throw new RuntimeException("根据过滤条件删除向量失败", e);
            }
        } catch (UnsupportedOperationException e) {
            // 如果过滤器类型不支持，抛出更友好的异常
            throw new UnsupportedFeatureException(
                    "不支持的 Filter 类型: " + e.getMessage()
            );
        }
    }

    /**
     * 移除所有向量
     */
    @Override
    public void removeAll() {
        String truncateSql = String.format(TRUNCATE_QUERY_TEMPLATE, tableName);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(truncateSql);
            log.info("清空了表 {}", tableName);
        } catch (SQLException e) {
            throw new RuntimeException("清空表失败", e);
        }
    }

    /**
     * 关闭数据源连接
     */
    @Override
    public void close() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
            log.info("数据源已关闭");
        }
    }
}
