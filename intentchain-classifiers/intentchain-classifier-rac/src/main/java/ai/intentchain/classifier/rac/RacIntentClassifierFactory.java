package ai.intentchain.classifier.rac;

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
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
public class RacIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "rac";

    public static final ConfigOption<Map<String, String>> CATEGORIES =
            ConfigOptions.key("categories")
                    .mapType()
                    .noDefaultValue()
                    .withDescription("""
                            A map containing name and description of texts for each category.
                            Providing more detailed description per category generally improves classification accuracy.
                            For example:
                            ```
                            categories:
                              billing_and_payments: Billing and payments.
                              technical_support: Technical support.
                              account_management: Account management.
                              product_information: Product information.
                              order_status: Order status.
                              returns_and_exchanges: Returns and exchanges.
                              feedback_and_complaints: Feedback and complaints.
                            ```
                            """);

    public static final ConfigOption<List<String>> FALLBACK_CATEGORIES =
            ConfigOptions.key("fallback-categories")
                    .stringType()
                    .asList()
                    .noDefaultValue()
                    .withDescription("The fallback category names.");

    public static final ConfigOption<String> INSTRUCTION =
            ConfigOptions.key("instruction")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Input additional instruction to help it better understand how to categorize intent.");

    public static final ConfigOption<Integer> MAX_RESULTS =
            ConfigOptions.key("max-results")
                    .intType()
                    .defaultValue(10)
                    .withDescription("Retrieve TopK maximum value, must be greater than or equal to 1");

    public static final ConfigOption<Double> MIN_SCORE =
            ConfigOptions.key("min-score")
                    .doubleType()
                    .defaultValue(0.6)
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
                    .defaultValue(10)
                    .withDescription("Re-rank TopK maximum value, must be between 1 and max-results, " +
                                     "and maximum not exceed 20");

    public static final ConfigOption<Double> RERANK_MIN_SCORE =
            ConfigOptions.key("rerank-min-score")
                    .doubleType()
                    .noDefaultValue()
                    .withDescription("Re-rank Score minimum value");

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using the RAC (Retrieval-Augmented Classification).";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return new LinkedHashSet<>(List.of(CATEGORIES));
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(
                FALLBACK_CATEGORIES, INSTRUCTION, MAX_RESULTS, MIN_SCORE,
                RERANK_MODE, RERANK_MAX_RESULTS, RERANK_MIN_SCORE));
    }

    @Override
    public Set<ConfigOption<?>> fingerprintOptions() {
        return Collections.emptySet();
    }

    @Override
    public boolean useEmbeddingStore() {
        return true;
    }

    @Override
    public IntentClassifier create(@NonNull String name,
                                   @NonNull ReadableConfig config,
                                   EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   ScoringModel scoringModel,
                                   ChatModel chatModel) {
        FactoryUtil.validateFactoryOptions(this, config);
        validateConfigOptions(config, embeddingModel, embeddingStore, scoringModel, chatModel);

        Map<String, String> categories = config.get(CATEGORIES);
        RacIntentClassifier.RacIntentClassifierBuilder builder = RacIntentClassifier.builder()
                .name(name)
                .chatModel(chatModel)
                .categories(categories.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> StringUtils.isBlank(e.getValue()) ?
                                        "<no description>" : e.getValue())
                        )
                )
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore);

        config.getOptional(FALLBACK_CATEGORIES).ifPresent(builder::fallbackCategories);
        config.getOptional(INSTRUCTION).ifPresent(builder::instruction);

        config.getOptional(MAX_RESULTS).ifPresent(builder::maxResults);
        config.getOptional(MIN_SCORE).ifPresent(builder::minScore);

        Optional.ofNullable(scoringModel).ifPresent(builder::scoringModel);
        config.getOptional(RERANK_MODE).ifPresent(builder::rerankMode);
        config.getOptional(RERANK_MAX_RESULTS).ifPresent(builder::rerankMaxResults);
        config.getOptional(RERANK_MIN_SCORE).ifPresent(builder::rerankMinScore);

        return builder.build();
    }

    private void validateConfigOptions(ReadableConfig config,
                                       EmbeddingModel embeddingModel,
                                       EmbeddingStore<TextSegment> embeddingStore,
                                       ScoringModel scoringModel,
                                       ChatModel chatModel) {
        Preconditions.checkArgument(chatModel != null, "llm has not been set yet");
        Preconditions.checkArgument(embeddingModel != null, "embedding has not been set yet");
        Preconditions.checkArgument(embeddingStore != null, "embedding_store has not been set yet");
        Boolean rerankMode = config.get(RERANK_MODE);
        Preconditions.checkArgument(!rerankMode || scoringModel != null,
                "'" + RERANK_MODE.key() + "' is true, reranking has not been set yet");

        Integer maxResults = config.get(MAX_RESULTS);
        Preconditions.checkArgument(maxResults >= 1 && maxResults <= 200,
                "'" + MAX_RESULTS.key() + "' value must be between 1 and 200");
        Double minScore = config.get(MIN_SCORE);
        Preconditions.checkArgument(minScore >= 0.0 && minScore <= 1.0,
                "'" + MIN_SCORE.key() + "' value must be between 0.0 and 1.0");

        Integer rerankMaxResults = config.get(RERANK_MAX_RESULTS);
        int rerankMaxResultsUpperLimit = Math.min(maxResults, 20);
        Preconditions.checkArgument(rerankMaxResults >= 1 && rerankMaxResults <= rerankMaxResultsUpperLimit,
                "'" + RERANK_MAX_RESULTS.key() + "' value must be between 1 and " + rerankMaxResultsUpperLimit);
    }
}
