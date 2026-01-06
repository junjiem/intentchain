package ai.intentchain.storer.tidb;

import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ConfigOptions;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.factories.EmbeddingStoreFactory;
import ai.intentchain.core.utils.FactoryUtil;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TiDB Embedding Store Factory
 */
public class TiDBEmbeddingStoreFactory implements EmbeddingStoreFactory {

    public static final String IDENTIFIER = "tidb";

    public static final String DEFAULT_TABLE_NAME_PREFIX = "ic_embeddings";

    public static final ConfigOption<String> JDBC_URL =
            ConfigOptions.key("jdbc-url")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("JDBC url of the TiDB server. (e.g., jdbc:mysql://localhost:4000)");

    public static final ConfigOption<String> HOST =
            ConfigOptions.key("host")
                    .stringType()
                    .defaultValue("localhost")
                    .withDescription("Hostname of the TiDB server.");

    public static final ConfigOption<Integer> PORT =
            ConfigOptions.key("port")
                    .intType()
                    .defaultValue(4000)
                    .withDescription("Port number of the TiDB server.");

    public static final ConfigOption<String> USERNAME =
            ConfigOptions.key("username")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Username for database authentication.");

    public static final ConfigOption<String> PASSWORD =
            ConfigOptions.key("password")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Password for database authentication.");

    public static final ConfigOption<String> DATABASE =
            ConfigOptions.key("database")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Name of the database to connect to.");

    public static final ConfigOption<String> TABLE_PREFIX =
            ConfigOptions.key("table-prefix")
                    .stringType()
                    .defaultValue(DEFAULT_TABLE_NAME_PREFIX)
                    .withDescription("The name prefix of the database table used for storing embeddings.");

    public static final ConfigOption<Integer> DIMENSION =
            ConfigOptions.key("dimension")
                    .intType()
                    .noDefaultValue()
                    .withDescription("The dimensionality of the embedding vectors. " +
                                     "This should match the embedding model being used.");

    public static final ConfigOption<String> DISTANCE_METRIC =
            ConfigOptions.key("distance-metric")
                    .stringType()
                    .defaultValue("cosine")
                    .withDescription("""
                            Vector distance metric. Available options:
                            - cosine: Cosine distance (default, supports vector index)
                            - l2: L2 distance / Euclidean distance (supports vector index)
                            - l1: L1 distance / Manhattan distance (no vector index support)
                            - inner_product: Inner product (no vector index support)
                            Note: Only 'cosine' and 'l2' support vector indexes for optimized queries.
                            """);

    public static final ConfigOption<Boolean> CREATE_TABLE =
            ConfigOptions.key("create-table")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription("Whether to automatically create the table if it doesn't exist.");

    public static final ConfigOption<Boolean> DROP_TABLE_FIRST =
            ConfigOptions.key("drop-table-first")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether to drop the table before creating it (useful for a fresh start).");

    public static final ConfigOption<Boolean> CREATE_VECTOR_INDEX =
            ConfigOptions.key("create-vector-index")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether to create a vector index to improve search performance. " +
                                     "Only effective for 'cosine' and 'l2' distance metrics.");

    public static final ConfigOption<Integer> MAX_POOL_SIZE =
            ConfigOptions.key("max-pool-size")
                    .intType()
                    .defaultValue(10)
                    .withDescription("Maximum number of connections in the connection pool.");

    public static final ConfigOption<Integer> MIN_IDLE_CONNECTIONS =
            ConfigOptions.key("min-idle-connections")
                    .intType()
                    .defaultValue(2)
                    .withDescription("Minimum number of idle connections in the connection pool.");

    public static final ConfigOption<Duration> CONNECTION_TIMEOUT =
            ConfigOptions.key("connection-timeout")
                    .durationType()
                    .defaultValue(Duration.ofSeconds(30))
                    .withDescription("Connection timeout.");

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return new LinkedHashSet<>(List.of(DIMENSION));
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(
                JDBC_URL, HOST, PORT, USERNAME, DATABASE,
                TABLE_PREFIX, DISTANCE_METRIC, CREATE_TABLE, DROP_TABLE_FIRST,
                CREATE_VECTOR_INDEX, MAX_POOL_SIZE, MIN_IDLE_CONNECTIONS, CONNECTION_TIMEOUT
        ));
    }

    @Override
    public Set<ConfigOption<?>> fingerprintOptions() {
        return Set.of(JDBC_URL, HOST, PORT, USERNAME, DATABASE, TABLE_PREFIX, DIMENSION, DISTANCE_METRIC);
    }

    @Override
    public EmbeddingStore<TextSegment> create(String storeId, ReadableConfig config) {
        FactoryUtil.validateFactoryOptions(this, config);

        String tableNamePrefix = config.get(TABLE_PREFIX);
        Integer dimension = config.get(DIMENSION);
        String distanceMetric = config.get(DISTANCE_METRIC);
        String tableName = String.join("_", tableNamePrefix, storeId.replace('-', '_'));

        TiDBEmbeddingStore.TiDBEmbeddingStoreBuilder builder = TiDBEmbeddingStore.builder()
                .tableName(tableName)
                .dimension(dimension)
                .distanceMetric(DistanceMetric.fromValue(distanceMetric));

        config.getOptional(JDBC_URL).ifPresent(builder::jdbcUrl);
        config.getOptional(HOST).ifPresent(builder::host);
        config.getOptional(PORT).ifPresent(builder::port);
        config.getOptional(DATABASE).ifPresent(builder::database);
        config.getOptional(USERNAME).ifPresent(builder::username);
        config.getOptional(PASSWORD).ifPresent(builder::password);

        config.getOptional(CREATE_TABLE).ifPresent(builder::createTable);
        config.getOptional(DROP_TABLE_FIRST).ifPresent(builder::dropTableFirst);
        config.getOptional(CREATE_VECTOR_INDEX).ifPresent(builder::createVectorIndex);

        config.getOptional(MAX_POOL_SIZE).ifPresent(builder::maxPoolSize);
        config.getOptional(MIN_IDLE_CONNECTIONS).ifPresent(builder::minIdleConnections);
        config.getOptional(CONNECTION_TIMEOUT).ifPresent(d -> builder.connectionTimeout(d.toMillis()));

        return builder.build();
    }
}

