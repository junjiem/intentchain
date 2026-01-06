package ai.intentchain.classifier.tidb;

import ai.intentchain.core.classifiers.IntentCache;
import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.classifiers.data.Intent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Intent classifier using the TiDB cache
 */
@Slf4j
public class TiDBCacheIntentClassifier implements IntentClassifier, IntentCache, AutoCloseable {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // SQL 语句模板
    private static final String CREATE_TABLE_TEMPLATE = """
            CREATE TABLE IF NOT EXISTS %s (
                cache_key VARCHAR(512) PRIMARY KEY,
                cache_value TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_updated_at (updated_at)
            )
            """;

    private static final String ALTER_TABLE_CACHE_TEMPLATE = """
            ALTER TABLE %s CACHE
            """;

    private static final String SELECT_CACHE_TEMPLATE = """
            SELECT cache_value FROM %s WHERE cache_key = ?
            """;

    private static final String UPSERT_CACHE_TEMPLATE = """
            INSERT INTO %s (cache_key, cache_value) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE cache_value = ?, updated_at = CURRENT_TIMESTAMP
            """;

    private static final String DELETE_CACHE_TEMPLATE = """
            DELETE FROM %s WHERE cache_key = ?
            """;

    private static final String CACHE_VALUE_COLUMN_LABEL = "cache_value";

    private final String name;
    private final DataSource dataSource;
    private final String tableName;
    private final Integer maxTextLength;
    private final Boolean useCachedTable;

    @Builder
    public TiDBCacheIntentClassifier(@NonNull String name,
                                     String jdbcUrl, String host, Integer port, String database,
                                     String username, String password,
                                     @NonNull String tableName,
                                     Integer maxTextLength,
                                     Boolean useCachedTable,
                                     Integer maxPoolSize,
                                     Integer minIdleConnections,
                                     Long connectionTimeout,
                                     Long idleTimeout,
                                     Long maxLifetime,
                                     Long leakDetectionThreshold) {
        this.name = name;
        this.tableName = tableName;
        this.maxTextLength = Optional.ofNullable(maxTextLength).orElse(128);
        this.useCachedTable = Optional.ofNullable(useCachedTable).orElse(true);

        // 初始化 HikariCP 连接池
        HikariConfig config = new HikariConfig();

        // Build JDBC URL if not provided
        if (jdbcUrl == null) {
            config.setJdbcUrl(
                    String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                            host, port, Optional.ofNullable(database).orElse(""))
            );
        } else {
            config.setJdbcUrl(jdbcUrl);
        }

        // 设置认证信息
        if (username != null) {
            config.setUsername(username);
        }
        if (password != null) {
            config.setPassword(password);
        }

        // 配置连接池参数
        config.setMaximumPoolSize(Optional.ofNullable(maxPoolSize).orElse(10));
        config.setMinimumIdle(Optional.ofNullable(minIdleConnections).orElse(2));
        config.setConnectionTimeout(Optional.ofNullable(connectionTimeout).orElse(30000L)); // 30s
        config.setIdleTimeout(Optional.ofNullable(idleTimeout).orElse(600000L)); // 10m
        config.setMaxLifetime(Optional.ofNullable(maxLifetime).orElse(1800000L)); // 30m
        config.setLeakDetectionThreshold(Optional.ofNullable(leakDetectionThreshold).orElse(60000L)); // 1m

        this.dataSource = new HikariDataSource(config);

        // 初始化表
        try {
            initializeTable();
            log.info("TiDB Cache - Initialized successfully with connection pool.");
        } catch (SQLException e) {
            log.error("TiDB Cache - Failed to initialize table", e);
            throw new RuntimeException("Failed to initialize TiDB cache table", e);
        }
    }

    private void initializeTable() throws SQLException {
        String createTableSQL = String.format(CREATE_TABLE_TEMPLATE, tableName);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            log.debug("TiDB Cache - Table '{}' initialized.", tableName);

            // 将表设置为缓存表（内存表），提升查询性能
            if (useCachedTable) {
                try {
                    String cacheTableSQL = String.format(ALTER_TABLE_CACHE_TEMPLATE, tableName);
                    stmt.execute(cacheTableSQL);
                    log.info("TiDB Cache - Table '{}' has been set as CACHED TABLE (in-memory).", tableName);
                } catch (SQLException e) {
                    // 表可能已经是缓存表，或者 TiDB 版本不支持，记录警告
                    log.warn("TiDB Cache - Failed to set table as CACHED TABLE (may already be cached or unsupported): {}",
                            e.getMessage());
                }
            } else {
                log.info("TiDB Cache - CACHED TABLE feature is disabled. Using regular table for '{}'.", tableName);
            }
        }
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("TiDB Cache - Start get cache content.");
        String selectSQL = String.format(SELECT_CACHE_TEMPLATE, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, text);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String cacheValue = rs.getString(CACHE_VALUE_COLUMN_LABEL);
                    String[] labels = cacheValue.split(",");
                    List<Intent> intents = new ArrayList<>();
                    for (String label : labels) {
                        if (label != null && !label.trim().isEmpty()) {
                            intents.add(Intent.from(label.trim()));
                        }
                    }
                    try {
                        log.debug("TiDB Cache - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
                    } catch (JsonProcessingException e) {
                        //
                    }
                    return intents;
                } else {
                    log.debug("TiDB Cache - Cache miss fallback.");
                    return Collections.emptyList();
                }
            }
        } catch (SQLException e) {
            log.error("TiDB Cache - Error retrieving cache", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void set(@NonNull String key, @NonNull List<String> value) {
        log.debug("TiDB Cache - Start set the cache.");

        if (key.length() > maxTextLength) {
            log.debug("TiDB Cache - The key length is greater than the " + maxTextLength + ", not be write to the cache.");
            return;
        }

        log.debug("TiDB Cache - Set key: " + key + ", and value: [" + String.join(",", value) + "]");

        String cacheValue = String.join(",", value);
        String upsertSQL = String.format(UPSERT_CACHE_TEMPLATE, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(upsertSQL)) {

            pstmt.setString(1, key);
            pstmt.setString(2, cacheValue);
            pstmt.setString(3, cacheValue);
            pstmt.executeUpdate();
            log.debug("TiDB Cache - The cache has been completed.");
        } catch (SQLException e) {
            log.error("TiDB Cache - Error setting cache", e);
        }
    }

    @Override
    public void del(@NonNull String key) {
        log.debug("TiDB Cache - Start delete cache the key: " + key);

        String deleteSQL = String.format(DELETE_CACHE_TEMPLATE, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

            pstmt.setString(1, key);
            int deleted = pstmt.executeUpdate();
            log.debug("TiDB Cache - The cache has been deleted. Rows affected: {}", deleted);
        } catch (SQLException e) {
            log.error("TiDB Cache - Error deleting cache", e);
        }
    }

    @Override
    public void close() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
            log.info("TiDB Cache - Connection pool closed for '{}'.", name);
        }
    }
}
