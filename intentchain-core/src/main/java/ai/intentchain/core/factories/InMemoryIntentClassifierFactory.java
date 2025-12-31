package ai.intentchain.core.factories;

import ai.intentchain.core.classifiers.InMemoryIntentClassifier;
import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ConfigOptions;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.utils.FactoryUtil;
import com.google.common.base.Preconditions;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.NonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class InMemoryIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "inmemory";

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
        return "Intent classifier using the in-memory cache.";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(MAX_TEXT_LENGTH));
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

        InMemoryIntentClassifier.InMemoryIntentClassifierBuilder builder = InMemoryIntentClassifier.builder()
                .name(name);
        config.getOptional(MAX_TEXT_LENGTH).ifPresent(builder::maxTextLength);
        return builder.build();
    }

    private void validateConfigOptions(ReadableConfig config) {
        Integer maxTextLength = config.get(MAX_TEXT_LENGTH);
        Preconditions.checkArgument(maxTextLength > 0,
                "'" + MAX_TEXT_LENGTH.key() + "' value must be greater than 0");
    }
}
