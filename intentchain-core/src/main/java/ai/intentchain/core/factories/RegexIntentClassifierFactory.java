package ai.intentchain.core.factories;

import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.classifiers.RegexIntentClassifier;
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

import java.util.*;

/**
 *
 */
public class RegexIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "regex";

    public static final ConfigOption<Map<String, List<String>>> REGEXS_BY_LABEL =
            ConfigOptions.key("regexs-by-label")
                    .mapListType()
                    .noDefaultValue()
                    .withDescription("""
                            A map containing regular expressions of texts for each label.
                            For example:
                            ```
                            regexs-by-label:
                              label1:
                                - ... # Regular expression
                              label2:
                                - ... # Regular expression
                                - ... # Regular expression
                            ```
                            """);

    private static final ConfigOption<Boolean> CASE_SENSITIVE =
            ConfigOptions.key("case-sensitive")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription("Case sensitive.");

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using regex matching.";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return new LinkedHashSet<>(List.of(REGEXS_BY_LABEL));
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

        Map<String, List<String>> regexsByLabel = config.get(REGEXS_BY_LABEL);
        Boolean caseSensitive = config.get(CASE_SENSITIVE);

        return new RegexIntentClassifier(name, caseSensitive, regexsByLabel);
    }
}
