package ai.intentchain.classifier.redis;

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

import java.util.*;

/**
 *
 */
public class RedisIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "redis";

    public static final ConfigOption<String> URI =
            ConfigOptions.key("uri")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Redis Stack Server URI. (e.g., redis://localhost:6379, rediss://localhost:6379)");

    public static final ConfigOption<String> HOST =
            ConfigOptions.key("host")
                    .stringType()
                    .defaultValue("localhost")
                    .withDescription("Redis Stack Server host.");

    public static final ConfigOption<Integer> PORT =
            ConfigOptions.key("port")
                    .intType()
                    .defaultValue(6379)
                    .withDescription("Redis Stack Server port.");

    public static final ConfigOption<String> USER =
            ConfigOptions.key("user")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Redis Stack username.");

    public static final ConfigOption<String> PASSWORD =
            ConfigOptions.key("password")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Redis Stack password.");

    public static final ConfigOption<String> PREFIX =
            ConfigOptions.key("prefix")
                    .stringType()
                    .defaultValue("intentchain:")
                    .withDescription("The prefix of the key, which should end with a colon (e.g., \"intentchain:\").");

    public static final ConfigOption<Integer> MAX_TEXT_LENGTH =
            ConfigOptions.key("max-text-length")
                    .intType()
                    .defaultValue(128)
                    .withDescription("Maximum number of characters allowed for the text to be cached." +
                                     "Texts whose length exceeds this value will not be written into the cache.");

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using the redis cache.";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(URI, HOST, PORT, USER, PASSWORD, PREFIX, MAX_TEXT_LENGTH));
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

        RedisIntentClassifier.RedisIntentClassifierBuilder builder = RedisIntentClassifier.builder()
                .name(name);
        config.getOptional(URI).ifPresent(builder::uri);
        config.getOptional(HOST).ifPresent(builder::host);
        config.getOptional(PORT).ifPresent(builder::port);
        config.getOptional(USER).ifPresent(builder::user);
        config.getOptional(PASSWORD).ifPresent(builder::password);
        config.getOptional(PREFIX).ifPresent(builder::prefix);
        config.getOptional(MAX_TEXT_LENGTH).ifPresent(builder::maxTextLength);
        return builder.build();
    }

    private void validateConfigOptions(ReadableConfig config) {
        Optional<String> uriOptional = config.getOptional(URI);
        if (uriOptional.isEmpty()) {
            Preconditions.checkArgument(config.getOptional(HOST).isPresent(),
                    "'" + HOST.key() + "' is required");
            Preconditions.checkArgument(config.getOptional(PORT).isPresent(),
                    "'" + PORT.key() + "' is required");
        }
        Integer maxTextLength = config.get(MAX_TEXT_LENGTH);
        Preconditions.checkArgument(maxTextLength > 0,
                "'" + MAX_TEXT_LENGTH.key() + "' value must be greater than 0");
    }
}
