package ai.intentchain.classifier.tidb;

import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ConfigOptions;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.factories.IntentClassifierFactory;
import ai.intentchain.core.utils.FactoryUtil;
import com.google.common.base.Preconditions;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.NonNull;

import java.time.Duration;
import java.util.*;

/**
 * TiDB Cache Intent Classifier Factory
 */
public class TiDBCacheIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "tidb-cache";

    public static final ConfigOption<String> JDBC_URL =
            ConfigOptions.key("jdbc-url")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("TiDB JDBC URL. (e.g., jdbc:mysql://localhost:4000/test)");

    public static final ConfigOption<String> HOST =
            ConfigOptions.key("host")
                    .stringType()
                    .defaultValue("localhost")
                    .withDescription("TiDB Server host.");

    public static final ConfigOption<Integer> PORT =
            ConfigOptions.key("port")
                    .intType()
                    .defaultValue(4000)
                    .withDescription("TiDB Server port.");

    public static final ConfigOption<String> DATABASE =
            ConfigOptions.key("database")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("TiDB database name.");

    public static final ConfigOption<String> USERNAME =
            ConfigOptions.key("username")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("TiDB username.");

    public static final ConfigOption<String> PASSWORD =
            ConfigOptions.key("password")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("TiDB password.");

    public static final ConfigOption<String> TABLE_NAME =
            ConfigOptions.key("table-name")
                    .stringType()
                    .defaultValue("intentchain_cache")
                    .withDescription("The name of the table in TiDB.");

    public static final ConfigOption<Boolean> USE_CACHED_TABLE =
            ConfigOptions.key("use-cached-table")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription("Whether to use TiDB CACHED TABLE feature for in-memory caching. " +
                                     "This significantly improves query performance by caching the entire table in memory.");

    public static final ConfigOption<Integer> MAX_TEXT_LENGTH =
            ConfigOptions.key("max-text-length")
                    .intType()
                    .defaultValue(128)
                    .withDescription("Maximum number of characters allowed for the text to be cached. " +
                                     "Texts whose length exceeds this value will not be written into the cache.");

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

    public static final ConfigOption<Duration> IDLE_TIMEOUT =
            ConfigOptions.key("idle-timeout")
                    .durationType()
                    .defaultValue(Duration.ofMinutes(10))
                    .withDescription("Idle timeout for connections.");

    public static final ConfigOption<Duration> MAX_LIFETIME =
            ConfigOptions.key("max-lifetime")
                    .durationType()
                    .defaultValue(Duration.ofMinutes(30))
                    .withDescription("Maximum lifetime of a connection.");

    public static final ConfigOption<Duration> LEAK_DETECTION_THRESHOLD =
            ConfigOptions.key("leak-detection-threshold")
                    .durationType()
                    .defaultValue(Duration.ofMinutes(1))
                    .withDescription("Leak detection threshold for connections.");

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using the TiDB cache with connection pooling.";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(
                JDBC_URL, HOST, PORT, DATABASE, USERNAME, PASSWORD,
                TABLE_NAME, USE_CACHED_TABLE, MAX_TEXT_LENGTH,
                MAX_POOL_SIZE, MIN_IDLE_CONNECTIONS, CONNECTION_TIMEOUT,
                IDLE_TIMEOUT, MAX_LIFETIME, LEAK_DETECTION_THRESHOLD
        ));
    }

    @Override
    public IntentClassifier create(@NonNull String name,
                                   @NonNull ReadableConfig config,
                                   EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   ScoringModel scoringModel,
                                   ChatModel chatModel) {
        FactoryUtil.validateFactoryOptions(this, config);
        validateConfigOptions(config);

        String tableName = config.get(TABLE_NAME);

        TiDBCacheIntentClassifier.TiDBCacheIntentClassifierBuilder builder = TiDBCacheIntentClassifier.builder()
                .name(name)
                .tableName(tableName);

        config.getOptional(JDBC_URL).ifPresent(builder::jdbcUrl);
        config.getOptional(HOST).ifPresent(builder::host);
        config.getOptional(PORT).ifPresent(builder::port);
        config.getOptional(DATABASE).ifPresent(builder::database);
        config.getOptional(USERNAME).ifPresent(builder::username);
        config.getOptional(PASSWORD).ifPresent(builder::password);

        config.getOptional(USE_CACHED_TABLE).ifPresent(builder::useCachedTable);
        config.getOptional(MAX_TEXT_LENGTH).ifPresent(builder::maxTextLength);

        config.getOptional(MAX_POOL_SIZE).ifPresent(builder::maxPoolSize);
        config.getOptional(MIN_IDLE_CONNECTIONS).ifPresent(builder::minIdleConnections);
        config.getOptional(CONNECTION_TIMEOUT).ifPresent(d -> builder.connectionTimeout(d.toMillis()));
        config.getOptional(IDLE_TIMEOUT).ifPresent(d -> builder.idleTimeout(d.toMillis()));
        config.getOptional(MAX_LIFETIME).ifPresent(d -> builder.maxLifetime(d.toMillis()));
        config.getOptional(LEAK_DETECTION_THRESHOLD).ifPresent(d -> builder.leakDetectionThreshold(d.toMillis()));

        return builder.build();
    }

    private void validateConfigOptions(ReadableConfig config) {
        Optional<String> jdbcUrlOptional = config.getOptional(JDBC_URL);
        if (jdbcUrlOptional.isEmpty()) {
            Preconditions.checkArgument(config.getOptional(HOST).isPresent(),
                    "'" + HOST.key() + "' is required when '" + JDBC_URL.key() + "' is not provided");
            Preconditions.checkArgument(config.getOptional(PORT).isPresent(),
                    "'" + PORT.key() + "' is required when '" + JDBC_URL.key() + "' is not provided");
        }

        Integer maxTextLength = config.get(MAX_TEXT_LENGTH);
        Preconditions.checkArgument(maxTextLength > 0,
                "'" + MAX_TEXT_LENGTH.key() + "' value must be greater than 0");

        Integer maxPoolSize = config.get(MAX_POOL_SIZE);
        Preconditions.checkArgument(maxPoolSize > 0,
                "'" + MAX_POOL_SIZE.key() + "' value must be greater than 0");
        Integer minIdleConnections = config.get(MIN_IDLE_CONNECTIONS);
        Preconditions.checkArgument(minIdleConnections >= 0,
                "'" + MIN_IDLE_CONNECTIONS.key() + "' value must be greater than or equal to 0");
        Preconditions.checkArgument(minIdleConnections <= maxPoolSize,
                "'" + MIN_IDLE_CONNECTIONS.key() + "' (" + minIdleConnections +
                ") must be less than or equal to '" + MAX_POOL_SIZE.key() + "' (" + maxPoolSize + ")");
    }
}
