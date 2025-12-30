package ai.intentchain.classifier.retrieval;

import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.classifiers.IntentTrainer;
import ai.intentchain.core.classifiers.data.Intent;
import ai.intentchain.core.classifiers.data.TextLabel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Intent classifier using the retrieval
 */
@Slf4j
public class RetrievalIntentClassifier implements IntentTrainer, IntentClassifier {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String LABEL = "label";

    private final String name;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private final Integer maxResults;
    private final Double minScore;

    private final ScoringModel scoringModel;

    private final Boolean rerankMode;
    private final Integer rerankMaxResults;
    private final Double rerankMinScore;

    private final Double threshold; // Confidence Threshold
    private final Double ambiguityThreshold; // Ambiguity Threshold

    @Builder
    public RetrievalIntentClassifier(@NonNull String name,
                                     @NonNull EmbeddingModel embeddingModel,
                                     @NonNull EmbeddingStore<TextSegment> embeddingStore,
                                     Integer maxResults, Double minScore,
                                     ScoringModel scoringModel, Boolean rerankMode,
                                     Integer rerankMaxResults, Double rerankMinScore,
                                     Double threshold, Double ambiguityThreshold) {
        this.name = name;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.scoringModel = scoringModel;

        this.rerankMode = Optional.ofNullable(rerankMode).orElse(false);
        this.maxResults = Optional.ofNullable(maxResults).orElse(1);
        int maxResultsUpperLimit = this.rerankMode ? 20 : 3;
        Preconditions.checkArgument(this.maxResults <= maxResultsUpperLimit && this.maxResults >= 1,
                "maxResults must be between 1 and %s", maxResultsUpperLimit);
        this.minScore = Optional.ofNullable(minScore).orElse(0.8);
        Preconditions.checkArgument(this.minScore >= 0.0 && this.minScore <= 1.0,
                "minScore must be between 0.0 and 1.0");

        Preconditions.checkArgument(!this.rerankMode || this.scoringModel != null,
                "scoringModel cannot be null when rerankMode is true");
        this.rerankMaxResults = Optional.ofNullable(rerankMaxResults).orElse(this.maxResults);
        int rerankMaxResultsUpperLimit = Math.min(this.maxResults, 3);
        Preconditions.checkArgument(this.rerankMaxResults <= rerankMaxResultsUpperLimit
                                    && this.rerankMaxResults >= 1,
                "rerankMaxResults must be between 1 and %s", rerankMaxResultsUpperLimit);
        this.rerankMinScore = rerankMinScore;
        Preconditions.checkArgument(this.rerankMinScore == null
                                    || (this.rerankMinScore >= 0.0 && this.rerankMinScore <= 1.0),
                "rerankMinScore must be between 0.0 and 1.0");

        double thresholdLowerLimit = this.rerankMode ?
                Optional.ofNullable(this.rerankMinScore).orElse(0.0) : this.minScore;
        double defaultThreshold = this.rerankMode ?
                Optional.ofNullable(this.rerankMinScore).orElse(0.8) : this.minScore;
        this.threshold = Optional.ofNullable(threshold).orElse(defaultThreshold);
        Preconditions.checkArgument(this.threshold >= thresholdLowerLimit && this.threshold <= 1.0,
                "threshold must be between %s and 1.0", thresholdLowerLimit);
        this.ambiguityThreshold = Optional.ofNullable(ambiguityThreshold).orElse(0.01);
        Preconditions.checkArgument(this.ambiguityThreshold >= 0.0 && this.ambiguityThreshold <= 1.0,
                "ambiguityThreshold must be between 0.0 and 1.0");
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("Retrieval - Start retrieve contents.");
        Query query = Query.from(text);
        List<Content> contents = buildContentRetriever().retrieve(query);
        if (rerankMode && scoringModel != null && !contents.isEmpty()) {
            contents = buildContentAggregator()
                    .aggregate(Collections.singletonMap(query, Collections.singletonList(contents)));
        }
        log.debug("Retrieval - The total of " + contents.size() + " contents were retrieved.");
        if (contents.isEmpty()) {
            log.debug("Retrieval - Retrieve contents is empty fallback.");
            return Collections.emptyList();
        }
        List<Intent> intents = contents.stream()
                .filter(c -> c.textSegment().metadata().containsKey(LABEL))
                .map(c -> Intent.from(c.textSegment().metadata().getString(LABEL),
                        (Double) c.metadata().getOrDefault(ContentMetadata.SCORE, 1)))
                // Distinct
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                Intent::getLabel,
                                o -> o,
                                (o1, o2) -> o1,
                                LinkedHashMap::new  // Use LinkedHashMap to maintain order
                        ),
                        map -> map.values().stream().toList()
                ));
        try {
            log.debug("Retrieval - The retrieved intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        // All TopN Confidence < Threshold ? -> Fallback
        log.debug("Retrieval - Start confidence threshold check. (confidenceThreshold: " + threshold + ")");
        List<Intent> outputs = intents.stream().filter(i -> i.getScore() >= threshold).toList();
        if (outputs.isEmpty()) {
            log.debug("Retrieval - Confidence threshold check fallback.");
            return Collections.emptyList();
        }
        if (intents.size() >= 2) {
            // Top1 Confidence - Top2 Confidence < ambiguityThreshold ? -> Fallback
            log.debug("Retrieval - Start ambiguity threshold check. (ambiguityThreshold: " + ambiguityThreshold + ")");
            if (intents.get(0).getScore() - intents.get(1).getScore() < ambiguityThreshold) {
                log.debug("Retrieval - Ambiguity threshold check fallback.");
                return Collections.emptyList();
            }
        }
        try {
            log.debug("Retrieval - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        return outputs;
    }

    public ContentRetriever buildContentRetriever() {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

    public ContentAggregator buildContentAggregator() {
        ReRankingContentAggregator.ReRankingContentAggregatorBuilder builder =
                ReRankingContentAggregator.builder()
                        .scoringModel(scoringModel)
                        .maxResults(rerankMaxResults);
        Optional.ofNullable(rerankMinScore).ifPresent(builder::minScore);
        return builder.build();
    }

    @Override
    public List<String> train(@NonNull List<String> ids, @NonNull List<TextLabel> textLabels) {
        log.debug("Retrieval - Start training " + textLabels.size() + " pieces of data.");
        List<TextSegment> textSegments = textLabels.stream()
                .map(d -> TextSegment.from(d.getText(), Metadata.from(LABEL, d.getLabel())))
                .toList();
        List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();
        embeddingStore.addAll(ids, embeddings, textSegments);
        log.debug("Retrieval - Training has been completed.");
        return ids;
    }

    @Override
    public void remove(@NonNull Collection<String> ids) {
        log.debug("Retrieval - Start remove the training data: " + String.join(", ", ids));
        embeddingStore.removeAll(ids);
        log.debug("Retrieval - The training data has been removed.");
    }
}
