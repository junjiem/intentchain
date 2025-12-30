package ai.intentchain.sdk;

import ai.intentchain.core.classifiers.data.TrainingData;
import lombok.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ChangeTrainingDataCacheUtil {

    private ChangeTrainingDataCacheUtil() {
    }

    // Map<projectId, Map<yamlFileRelativePath, List<TrainingData>>>
    private final static Map<String, Map<String, List<TrainingData>>> CACHE = new HashMap<>();

    public static void add(@NonNull String projectId, @NonNull String relativePath,
                           @NonNull List<TrainingData> trainingData) {
        Map<String, List<TrainingData>> map = new HashMap<>();
        if (CACHE.containsKey(projectId)) {
            map = CACHE.get(projectId);
        }
        map.put(relativePath, trainingData);
        CACHE.put(projectId, map);
    }

    public static Map<String, List<TrainingData>> remove(String projectId) {
        if (CACHE.containsKey(projectId)) {
            return CACHE.remove(projectId);
        }
        return Collections.emptyMap();
    }
}
