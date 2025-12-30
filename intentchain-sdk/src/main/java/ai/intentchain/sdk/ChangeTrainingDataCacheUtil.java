package ai.intentchain.sdk;

import ai.intentchain.core.classifiers.data.TextLabel;
import lombok.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ChangeTrainingDataCacheUtil {

    private ChangeTrainingDataCacheUtil() {
    }

    // Map<projectId, Map<fileRelativePath, List<TextLabel>>>
    private final static Map<String, Map<String, List<TextLabel>>> CACHE = new HashMap<>();

    public static void add(@NonNull String projectId, @NonNull String relativePath,
                           @NonNull List<TextLabel> textLabels) {
        Map<String, List<TextLabel>> map = new HashMap<>();
        if (CACHE.containsKey(projectId)) {
            map = CACHE.get(projectId);
        }
        map.put(relativePath, textLabels);
        CACHE.put(projectId, map);
    }

    public static Map<String, List<TextLabel>> remove(String projectId) {
        if (CACHE.containsKey(projectId)) {
            return CACHE.remove(projectId);
        }
        return Collections.emptyMap();
    }
}
