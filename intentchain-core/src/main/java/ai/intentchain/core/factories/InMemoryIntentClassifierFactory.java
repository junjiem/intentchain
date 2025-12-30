package ai.intentchain.core.factories;

import ai.intentchain.core.classifiers.InMemoryIntentClassifier;
import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ReadableConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.NonNull;

import java.util.Collections;
import java.util.Set;

/**
 *
 */
public class InMemoryIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "inmemory";

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using the in-memory cache.";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return Collections.emptySet();
    }

    @Override
    public IntentClassifier create(@NonNull String name,
                                   @NonNull ReadableConfig config,
                                   EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   ScoringModel scoringModel,
                                   ChatModel chatModel) {
        return new InMemoryIntentClassifier(name);
    }
}
