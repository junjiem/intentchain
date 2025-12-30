package ai.intentchain.core.factories;

import ai.intentchain.core.classifiers.DefaultIntentClassifier;
import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ConfigOptions;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.factories.IntentClassifierFactory;
import ai.intentchain.core.utils.FactoryUtil;
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
public class DefaultIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "default";

    private static final ConfigOption<List<String>> DEFAULTS =
            ConfigOptions.key("defaults")
                    .stringType()
                    .asList()
                    .defaultValues("other")
                    .withDescription("Default value list. " +
                                     "For example: [\"other\"]");

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using the defaults.";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(DEFAULTS));
    }

    @Override
    public IntentClassifier create(@NonNull String name,
                                   @NonNull ReadableConfig config,
                                   EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   ScoringModel scoringModel,
                                   ChatModel chatModel) {
        FactoryUtil.validateFactoryOptions(this, config);

        List<String> defaults = config.get(DEFAULTS);

        return new DefaultIntentClassifier(name, defaults);
    }
}
