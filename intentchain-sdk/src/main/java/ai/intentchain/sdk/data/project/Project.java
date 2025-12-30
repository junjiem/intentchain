package ai.intentchain.sdk.data.project;

import ai.intentchain.core.configuration.Configuration;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.factories.EmbeddingModelFactoryManager;
import ai.intentchain.core.factories.EmbeddingStoreFactoryManager;
import ai.intentchain.core.factories.ScoringModelFactoryManager;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.*;

@Setter
@Getter
public class Project {

    @NonNull
    private Integer version = 1;

    @NonNull
    private String name;

    private String description;

    @JsonIgnore
    @NonNull
    private ReadableConfig configuration = new Configuration();

    @JsonProperty("configuration")
    public void setConfiguration(Map<String, Object> configs) {
        this.configuration = Configuration.fromMap(configs);
    }

    @NonNull
    private List<String> chain = Collections.emptyList();

    private EmbeddingConfig embedding;

    public EmbeddingConfig getEmbedding() {
        if (embedding == null
            && EmbeddingModelFactoryManager.isSupported(EmbeddingConfig.DEFAULT_PROVIDER)) {
            embedding = new EmbeddingConfig();
        }
        return embedding;
    }

    @JsonProperty("embedding_store")
    private EmbeddingStoreConfig embeddingStore;

    public EmbeddingStoreConfig getEmbeddingStore() {
        if (embeddingStore == null
            && EmbeddingStoreFactoryManager.isSupported(EmbeddingStoreConfig.DEFAULT_PROVIDER)) {
            embeddingStore = new EmbeddingStoreConfig();
        }
        return embeddingStore;
    }

    private LlmConfig llm;

    private RerankingConfig reranking;

    public RerankingConfig getReranking() {
        if (reranking == null
            && ScoringModelFactoryManager.isSupported(RerankingConfig.DEFAULT_PROVIDER)) {
            reranking = new RerankingConfig();
        }
        return reranking;
    }

    @NonNull
    private List<ClassifierConfig> classifiers = Collections.singletonList(new ClassifierConfig());

    @JsonProperty("classifiers")
    public void setClassifiers(List<ClassifierConfig> classifiers) {
        Set<String> names = new HashSet<>();
        for (ClassifierConfig classifier : classifiers) {
            String name = classifier.getName();
            Preconditions.checkArgument(names.add(name),
                    String.format("There is duplicate classifier name '%s' in the classifiers", name));
        }
        this.classifiers = classifiers;
    }
}