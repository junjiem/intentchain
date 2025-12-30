package ai.intentchain.core.factories;

import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ReadableConfig;
import com.google.common.base.Preconditions;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface IntentClassifierFactory extends Factory {

    String factoryDescription();

    default Set<ConfigOption<?>> fingerprintOptions() {
        return Collections.emptySet();
    }

    default Map<String, String> fingerprintConfigs(@NonNull ReadableConfig config) {
        Set<ConfigOption<?>> fingerprintOptions = fingerprintOptions();
        Preconditions.checkArgument(fingerprintOptions != null,
                "The fingerprintOptions method cannot return null");
        List<String> keys = fingerprintOptions.stream()
                .map(ConfigOption::key)
                .toList();
        return config.toMap().entrySet().stream()
                .filter(e -> keys.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    default boolean useEmbeddingStore(){
        return false;
    }

    IntentClassifier create(@NonNull String name,
                            @NonNull ReadableConfig config,
                            EmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            ScoringModel scoringModel,
                            ChatModel chatModel);
}
