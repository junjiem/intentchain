package ai.intentchain.core.factories;

import ai.intentchain.core.classifiers.EmbeddingIntentClassifier;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class EmbeddingIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "embedding";

    public static final ConfigOption<Map<String, List<String>>> EXAMPLES_BY_LABEL =
            ConfigOptions.key("examples-by-label")
                    .mapListType()
                    .noDefaultValue()
                    .withDescription("""
                            A map containing examples of texts for each label.
                            Providing more examples per label generally improves classification accuracy.
                            For example:
                            ```
                            examples-by-label:
                              BILLING_AND_PAYMENTS:
                                - Can I pay using PayPal?
                                - Do you accept Bitcoin?
                                - Is it possible to pay via wire transfer?
                                - I keep getting an error message when I try to pay.
                              TECHNICAL_SUPPORT:
                                - The app keeps crashing whenever I open it.
                                - I can't save changes in the settings.
                                - Why is the search function not working?
                              ...
                            ```
                            """);

    public static final ConfigOption<Integer> MAX_RESULTS =
            ConfigOptions.key("max-results")
                    .intType()
                    .defaultValue(1)
                    .withDescription("The maximum number of labels to return for each classification, " +
                                     "must be between 1 and 3");

    public static final ConfigOption<Double> MIN_SCORE =
            ConfigOptions.key("min-score")
                    .doubleType()
                    .defaultValue(0.8)
                    .withDescription("""
                            The minimum similarity score required for classification, in the range [0..1].
                            Labels scoring lower than this value will be discarded.
                            """);

    public static final ConfigOption<Double> MEAN_TO_MAX_SCORE_RATIO =
            ConfigOptions.key("mean-to-max-score-ratio")
                    .doubleType()
                    .defaultValue(0.5)
                    .withDescription("""
                            A ratio, in the range [0..1], between the mean and max scores used for calculating the final score.
                            During classification, the embeddings of examples for each label are compared to the embedding of the text being classified.
                            This results in two metrics: the mean and max scores.
                            The mean score is the average similarity score for all examples associated with a given label.
                            The max score is the highest similarity score, corresponding to the example most similar to the text being classified.
                            A value of 0 means that only the mean score will be used for ranking labels.
                            A value of 0.5 means that both scores will contribute equally to the final score.
                            A value of 1 means that only the max score will be used for ranking labels.
                            """);

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using the embedding model.";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return new LinkedHashSet<>(List.of(EXAMPLES_BY_LABEL));
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(MAX_RESULTS, MIN_SCORE, MEAN_TO_MAX_SCORE_RATIO));
    }

    @Override
    public IntentClassifier create(@NonNull String name,
                                   @NonNull ReadableConfig config,
                                   EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   ScoringModel scoringModel,
                                   ChatModel chatModel) {
        FactoryUtil.validateFactoryOptions(this, config);
        validateConfigOptions(config, embeddingModel);

        Map<String, List<String>> examplesByLabel = config.get(EXAMPLES_BY_LABEL);

        EmbeddingIntentClassifier.EmbeddingIntentClassifierBuilder builder = EmbeddingIntentClassifier.builder()
                .name(name)
                .embeddingModel(embeddingModel)
                .examplesByLabel(examplesByLabel);

        config.getOptional(MAX_RESULTS).ifPresent(builder::maxResults);
        config.getOptional(MIN_SCORE).ifPresent(builder::minScore);
        config.getOptional(MEAN_TO_MAX_SCORE_RATIO).ifPresent(builder::meanToMaxScoreRatio);

        return builder.build();
    }

    private void validateConfigOptions(ReadableConfig config,
                                       EmbeddingModel embeddingModel) {
        Preconditions.checkArgument(embeddingModel != null, "embedding has not been set yet");
        Integer maxResults = config.get(MAX_RESULTS);
        Preconditions.checkArgument(maxResults >= 1 && maxResults <= 3,
                "'" + MAX_RESULTS.key() + "' value must be between 1 and 3");
        Double minScore = config.get(MIN_SCORE);
        Preconditions.checkArgument(minScore >= 0.0 && minScore <= 1.0,
                "'" + MIN_SCORE.key() + "' value must be between 0.0 and 1.0");
        Double meanToMaxScoreRatio = config.get(MEAN_TO_MAX_SCORE_RATIO);
        Preconditions.checkArgument(meanToMaxScoreRatio >= 0.0 && meanToMaxScoreRatio <= 1.0,
                "'" + MEAN_TO_MAX_SCORE_RATIO.key() + "' value must be between 0.0 and 1.0");
    }
}