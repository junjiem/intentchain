package ai.intentchain.core.factories;

import ai.intentchain.core.configuration.ReadableConfig;
import dev.langchain4j.model.scoring.ScoringModel;

public interface ScoringModelFactory extends Factory {
    ScoringModel create(ReadableConfig config);
}
