package ai.intentchain.core.classifiers;

import ai.intentchain.core.classifiers.data.Intent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Intent classifier using the in-memory cache
 */
@Slf4j
public class InMemoryIntentClassifier implements IntentClassifier, IntentCache {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final String name;

    private final ConcurrentHashMap<String, List<String>> cache = new ConcurrentHashMap<>();

    public InMemoryIntentClassifier(@NonNull String name) {
        this.name = name;
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("InMemory - Start get cache content.");
        List<String> labels = cache.get(text);
        if (labels == null || labels.isEmpty()) {
            log.debug("InMemory - Cache miss fallback.");
            return Collections.emptyList();
        }
        List<Intent> intents = labels.stream().map(Intent::from).collect(Collectors.toList());
        try {
            log.debug("InMemory - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        return intents;
    }

    @Override
    public void set(@NonNull String key, @NonNull List<String> value) {
        log.debug("InMemory - Start set the cache.");
        log.debug("InMemory - Set key: " + key + ", and value: [" + String.join(",", value) + "]");
        cache.put(key, value);
        log.debug("InMemory - The cache has been completed.");
    }

    @Override
    public void del(@NonNull String key) {
        log.debug("InMemory - Start delete cache the key: " + key);
        cache.remove(key);
        log.debug("InMemory - The cache has been deleted.");
    }
}
