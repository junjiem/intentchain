package ai.intentchain.classifier.retrieval;

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
public class RetrievalIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "retrieval";

    public static final ConfigOption<Integer> MAX_RESULTS =
            ConfigOptions.key("max-results")
                    .intType()
                    .defaultValue(1)
                    .withDescription("Retrieve TopK maximum value, must be greater than or equal to 1");

    public static final ConfigOption<Double> MIN_SCORE =
            ConfigOptions.key("min-score")
                    .doubleType()
                    .defaultValue(0.8)
                    .withDescription("Retrieve Score minimum value, must be between 0.0 and 1.0");

    public static final ConfigOption<Boolean> RERANK_MODE =
            ConfigOptions.key("rerank-mode")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Re-rank model will reorder the candidate content list based on " +
                                     "the semantic match with user query, improving the results of semantic ranking.");

    public static final ConfigOption<Integer> RERANK_MAX_RESULTS =
            ConfigOptions.key("rerank-max-results")
                    .intType()
                    .defaultValue(1)
                    .withDescription("Re-rank TopK maximum value, must be between 1 and max-results, " +
                                     "and maximum not exceed 3");

    public static final ConfigOption<Double> RERANK_MIN_SCORE =
            ConfigOptions.key("rerank-min-score")
                    .doubleType()
                    .noDefaultValue()
                    .withDescription("Re-rank Score minimum value, must be between 0.0 and 1.0");

    public static final ConfigOption<Double> THRESHOLD =
            ConfigOptions.key("threshold")
                    .doubleType()
                    .defaultValue(0.8)
                    .withDescription("Confidence threshold, must be between 0.0 and 1.0");

    public static final ConfigOption<Double> AMBIGUITY_THRESHOLD =
            ConfigOptions.key("ambiguity-threshold")
                    .doubleType()
                    .defaultValue(0.01)
                    .withDescription("Ambiguity threshold, must be between 0.0 and 1.0");

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using the retrieval.";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(
                MAX_RESULTS, MIN_SCORE,
                RERANK_MODE, RERANK_MAX_RESULTS, RERANK_MIN_SCORE,
                THRESHOLD, AMBIGUITY_THRESHOLD
        ));
    }

    @Override
    public Set<ConfigOption<?>> fingerprintOptions() {
        return Collections.emptySet();
    }

    @Override
    public IntentClassifier create(@NonNull String name,
                                   @NonNull ReadableConfig config,
                                   EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   ScoringModel scoringModel,
                                   ChatModel chatModel) {
        FactoryUtil.validateFactoryOptions(this, config);
        validateConfigOptions(config, embeddingModel, embeddingStore, scoringModel);

        RetrievalIntentClassifier.RetrievalIntentClassifierBuilder builder = RetrievalIntentClassifier.builder()
                .name(name)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore);

        config.getOptional(MAX_RESULTS).ifPresent(builder::maxResults);
        config.getOptional(MIN_SCORE).ifPresent(builder::minScore);

        Optional.ofNullable(scoringModel).ifPresent(builder::scoringModel);
        config.getOptional(RERANK_MODE).ifPresent(builder::rerankMode);
        config.getOptional(RERANK_MAX_RESULTS).ifPresent(builder::rerankMaxResults);
        config.getOptional(RERANK_MIN_SCORE).ifPresent(builder::rerankMinScore);

        config.getOptional(THRESHOLD).ifPresent(builder::threshold);
        config.getOptional(AMBIGUITY_THRESHOLD).ifPresent(builder::ambiguityThreshold);

        return builder.build();
    }

    private void validateConfigOptions(ReadableConfig config,
                                       EmbeddingModel embeddingModel,
                                       EmbeddingStore<TextSegment> embeddingStore,
                                       ScoringModel scoringModel) {
        Preconditions.checkArgument(embeddingModel != null, "embedding has not been set yet");
        Preconditions.checkArgument(embeddingStore != null, "embedding_store has not been set yet");
        Boolean rerankMode = config.get(RERANK_MODE);
        Preconditions.checkArgument(!rerankMode || scoringModel != null,
                "'" + RERANK_MODE.key() + "' is true, reranking has not been set yet");

        Integer maxResults = config.get(MAX_RESULTS);
        int maxResultsUpperLimit = rerankMode ? 20 : 3;
        Preconditions.checkArgument(maxResults >= 1 && maxResults <= maxResultsUpperLimit,
                "'" + MAX_RESULTS.key() + "' value must be between 1 and %s", maxResultsUpperLimit);
        Double minScore = config.get(MIN_SCORE);
        Preconditions.checkArgument(minScore >= 0.0 && minScore <= 1.0,
                "'" + MIN_SCORE.key() + "' value must be between 0.0 and 1.0");

        Integer rerankMaxResults = config.get(RERANK_MAX_RESULTS);
        int rerankMaxResultsUpperLimit = Math.min(maxResults, 3);
        Preconditions.checkArgument(rerankMaxResults >= 1 && rerankMaxResults <= rerankMaxResultsUpperLimit,
                "'" + RERANK_MAX_RESULTS.key() + "' value must be between 1 and " + rerankMaxResultsUpperLimit);
        Double rerankMinScore = config.get(RERANK_MIN_SCORE);
        Preconditions.checkArgument(rerankMinScore == null || (rerankMinScore >= 0.0 && rerankMinScore <= 1.0),
                "'" + RERANK_MIN_SCORE.key() + "' value must be between 0.0 and 1.0");
    }
}
