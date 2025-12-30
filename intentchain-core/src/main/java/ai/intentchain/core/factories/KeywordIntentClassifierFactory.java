package ai.intentchain.core.factories;

import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.classifiers.KeywordIntentClassifier;
import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ConfigOptions;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.utils.FactoryUtil;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.NonNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class KeywordIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "keyword";

    public static final ConfigOption<Map<String, List<String>>> KEYWORDS_BY_LABEL =
            ConfigOptions.key("keywords-by-label")
                    .mapListType()
                    .noDefaultValue()
                    .withDescription("""
                            A map containing keywords of texts for each label.
                            For example:
                            ```
                            keywords-by-label:
                              label1:
                                - keyword1
                              label2:
                                - keyword2
                                - keyword3
                            ```
                            """);

    private static final ConfigOption<Boolean> CASE_SENSITIVE =
            ConfigOptions.key("case-sensitive")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Case sensitive.");

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using simple keyword matching.";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return new LinkedHashSet<>(List.of(KEYWORDS_BY_LABEL));
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(CASE_SENSITIVE));
    }

    @Override
    public IntentClassifier create(@NonNull String name,
                                   @NonNull ReadableConfig config,
                                   EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   ScoringModel scoringModel,
                                   ChatModel chatModel) {
        FactoryUtil.validateFactoryOptions(this, config);

        Map<String, List<String>> keywordsByLabel = config.get(KEYWORDS_BY_LABEL);
        Boolean caseSensitive = config.get(CASE_SENSITIVE);

        return new KeywordIntentClassifier(name, caseSensitive, keywordsByLabel);
    }
}
