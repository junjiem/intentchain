package ai.intentchain.core.classifiers;

import ai.intentchain.core.classifiers.data.Intent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import dev.langchain4j.classification.EmbeddingModelTextClassifier;
import dev.langchain4j.classification.TextClassifier;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Intent classifier using the embedding model
 */
@Slf4j
public class EmbeddingIntentClassifier implements IntentClassifier {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final String name;

    private final TextClassifier<String> classifier;

    @Builder
    public EmbeddingIntentClassifier(@NonNull String name,
                                     @NonNull EmbeddingModel embeddingModel,
                                     @NonNull Map<String, List<String>> examplesByLabel,
                                     Integer maxResults, Double minScore,
                                     Double meanToMaxScoreRatio) {
        this.name = name;
        Preconditions.checkArgument(maxResults == null || (maxResults <= 3 && maxResults >= 1),
                "maxResults must be between 1 and 3");
        Preconditions.checkArgument(minScore == null || (minScore >= 0.0 && minScore <= 1.0),
                "minScore must be between 0.0 and 1.0");
        Preconditions.checkArgument(meanToMaxScoreRatio == null
                                    || (meanToMaxScoreRatio >= 0.0 && meanToMaxScoreRatio <= 1.0),
                "meanToMaxScoreRatio must be between 0.0 and 1.0");
        this.classifier = new EmbeddingModelTextClassifier<>(
                embeddingModel, examplesByLabel,
                Optional.ofNullable(maxResults).orElse(1),
                Optional.ofNullable(minScore).orElse(0.8),
                Optional.ofNullable(meanToMaxScoreRatio).orElse(0.5)
        );
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("Embedding - Start text classification.");
        List<Intent> intents = classifier.classifyWithScores(text).scoredLabels().stream()
                .map(sl -> Intent.from(sl.label(), sl.score())).collect(Collectors.toList());
        log.debug("Embedding - The total of " + intents.size() + " intents were classified.");
        if (intents.isEmpty()) {
            log.debug("Embedding - Text classification fallback.");
            return Collections.emptyList();
        }
        try {
            log.debug("Embedding - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        return intents;
    }
}
