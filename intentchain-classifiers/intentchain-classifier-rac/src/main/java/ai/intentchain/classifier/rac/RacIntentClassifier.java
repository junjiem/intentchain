package ai.intentchain.classifier.rac;

import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.classifiers.IntentTrainer;
import ai.intentchain.core.classifiers.data.Intent;
import ai.intentchain.core.classifiers.data.TextLabel;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Intent classifier using the RAC (Retrieval-Augmented Classification)
 */
@Slf4j
public class RacIntentClassifier implements IntentTrainer, IntentClassifier {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String LABEL = "label";

    private final String name;

    private final Assistant assistant;

    private final List<Category> categories;
    private final List<String> fallbackCategories;
    private final String instruction;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private final Integer maxResults;
    private final Double minScore;

    private final ScoringModel scoringModel;

    private final Boolean rerankMode;
    private final Integer rerankMaxResults;
    private final Double rerankMinScore;

    @Builder
    public RacIntentClassifier(@NonNull String name,
                               @NonNull ChatModel chatModel,
                               @NonNull Map<String, String> categories,
                               List<String> fallbackCategories, String instruction,
                               @NonNull EmbeddingModel embeddingModel,
                               @NonNull EmbeddingStore<TextSegment> embeddingStore,
                               Integer maxResults, Double minScore,
                               ScoringModel scoringModel, Boolean rerankMode,
                               Integer rerankMaxResults, Double rerankMinScore) {
        this.name = name;
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .build();

        this.categories = categories.entrySet().stream()
                .map(e -> new Category(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        this.fallbackCategories = Optional.ofNullable(fallbackCategories).orElse(Collections.emptyList());
        this.instruction = Optional.ofNullable(instruction).orElse("");

        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.scoringModel = scoringModel;

        this.rerankMode = Optional.ofNullable(rerankMode).orElse(false);
        this.maxResults = Optional.ofNullable(maxResults).orElse(10);
        Preconditions.checkArgument(this.maxResults <= 200 && this.maxResults >= 1,
                "maxResults must be between 1 and 200");
        this.minScore = Optional.ofNullable(minScore).orElse(0.6);
        Preconditions.checkArgument(this.minScore >= 0.0 && this.minScore <= 1.0,
                "minScore must be between 0.0 and 1.0");

        Preconditions.checkArgument(!this.rerankMode || this.scoringModel != null,
                "scoringModel cannot be null when rerankMode is true");
        this.rerankMaxResults = Optional.ofNullable(rerankMaxResults).orElse(this.maxResults);
        int rerankMaxResultsUpperLimit = Math.min(this.maxResults, 20);
        Preconditions.checkArgument(this.rerankMaxResults <= rerankMaxResultsUpperLimit
                                    && this.rerankMaxResults >= 1,
                "rerankMaxResults must be between 1 and %s", rerankMaxResultsUpperLimit);
        this.rerankMinScore = rerankMinScore;
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("RAC - Start retrieve contents.");
        Query query = Query.from(text);
        List<Content> contents = buildContentRetriever().retrieve(query);
        if (rerankMode && scoringModel != null && !contents.isEmpty()) {
            contents = buildContentAggregator()
                    .aggregate(Collections.singletonMap(query, Collections.singletonList(contents)));
        }
        Map<String, String> samples = contents.stream()
                .map(Content::textSegment)
                .filter(t -> t.metadata().containsKey(LABEL))
                // Distinct
                .collect(Collectors.toMap(TextSegment::text, t -> t.metadata().getString(LABEL), (o1, o2) -> o1));
        log.debug("RAC - The total of " + samples.size() + " samples were retrieved.");
        Set<String> sampleLabels = new HashSet<>(samples.values());
        if (!categories.stream().map(c -> c.name).collect(Collectors.toSet()).containsAll(sampleLabels)) {
            log.warn("RAC - The retrieved sample labels are not all contains in the categories." +
                     "\n  \t\tcategories: [" + categories.stream().map(c -> c.name).collect(Collectors.joining(", ")) + "], " +
                     "\n  \t\tsample_labels: [" + String.join(", ", sampleLabels) + "]");
        }
        log.debug("RAC - Start text classification.");
        String categoriesJson;
        try {
            categoriesJson = JSON_MAPPER.writeValueAsString(categories);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        CategoryResult result = assistant.textClassification(text, categoriesJson, samples, instruction);
        if (!fallbackCategories.isEmpty()) {
            log.debug("RAC - Start fallback categories check.");
            if (fallbackCategories.stream().anyMatch(c -> c.equals(result.category_name))) {
                log.debug("RAC - Categories check fallback.");
                return Collections.emptyList();
            }
        }
        if (categories.stream().noneMatch(c -> c.name.equals(result.category_name))) {
            log.debug("RAC - Category not exist fallback.");
            return Collections.emptyList();
        }
        List<Intent> intents = Collections.singletonList(Intent.from(result.category_name));
        try {
            log.debug("RAC - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        return intents;
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
        log.debug("RAC - Start training " + textLabels.size() + " pieces of data.");
        List<TextSegment> textSegments = textLabels.stream()
                .map(d -> TextSegment.from(d.getText(), Metadata.from(LABEL, d.getLabel())))
                .toList();
        List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();
        embeddingStore.addAll(ids, embeddings, textSegments);
        log.debug("RAC - Training has been completed.");
        return ids;
    }

    @Override
    public void remove(@NonNull Collection<String> ids) {
        log.debug("RAC - Start remove the training data: " + String.join(", ", ids));
        embeddingStore.removeAll(ids);
        log.debug("RAC - The training data has been removed.");
    }

    private interface Assistant {
        @UserMessage(fromResource = "prompts/rac/text_classification_user_prompt.txt")
        CategoryResult textClassification(@V("input_text") String inputText,
                                          @V("categories") String categoriesJson,
                                          @V("samples") Map<String, String> samples,
                                          @V("instruction") String instruction);
    }

    private record Category(@NonNull @JsonProperty("category_name") String name,
                            @NonNull @JsonProperty("category_description") String description) {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class CategoryResult {
        @Description("Extract the key words from the text that are related to the classification")
        private List<String> keywords;

        @Description("The category name of which category it belongs to")
        @JsonProperty("category_name")
        private String category_name;
    }
}
