package ai.intentchain.core.chain;

import ai.intentchain.core.chain.data.CascadeResult;
import ai.intentchain.core.classifiers.DefaultIntentClassifier;
import ai.intentchain.core.classifiers.IntentCache;
import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.classifiers.IntentTrainer;
import ai.intentchain.core.classifiers.data.Intent;
import ai.intentchain.core.classifiers.data.TextLabel;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cascade Intent Chain
 */
@Slf4j
public class CascadeIntentChain {
    private final List<IntentClassifier> classifiers;
    private final List<IntentCache> caches;
    private final Map<String, IntentTrainer> trainers;

    private final Boolean selfLearning;
    private final Double selfLearningThreshold;
    private final List<String> selfLearningExcludes;

    @Builder
    public CascadeIntentChain(@NonNull LinkedHashMap<String, IntentClassifier> classifiers,
                              Boolean selfLearning, Double selfLearningThreshold,
                              List<String> selfLearningExcludes) {
        this.classifiers = classifiers.values().stream().toList();
        this.caches = classifiers.values().stream()
                .filter(o -> o instanceof IntentCache)
                .map(o -> (IntentCache) o)
                .collect(Collectors.toList());
        this.trainers = classifiers.entrySet().stream()
                .filter(e -> e.getValue() instanceof IntentTrainer)
                .filter(e -> ((IntentTrainer) e.getValue()).isPersistent())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (IntentTrainer) e.getValue()));

        this.selfLearning = Optional.ofNullable(selfLearning).orElse(false);
        this.selfLearningThreshold = Optional.ofNullable(selfLearningThreshold).orElse(0.95);
        this.selfLearningExcludes = Optional.ofNullable(selfLearningExcludes).orElse(Collections.emptyList());
    }

    public CascadeResult classify(@NonNull String text) {
        Instant start = Instant.now();
        String traceId = UUID.randomUUID().toString();
        if (StringUtils.isBlank(text)) {
            Optional<IntentClassifier> classifierOptional = classifiers.stream()
                    .filter(c -> c instanceof DefaultIntentClassifier)
                    .findFirst();
            if (classifierOptional.isPresent()) {
                IntentClassifier classifier = classifierOptional.get();
                return new CascadeResult(traceId, text, classifier.classify(text),
                        Collections.singletonList(classifier.classifierName()), start);
            }
        }
        List<String> cascadePath = new ArrayList<>();
        List<Intent> intents = null;
        for (IntentClassifier classifier : classifiers) {
            cascadePath.add(classifier.classifierName());
            try {
                intents = classifier.classify(text);
            } catch (Exception e) {
                log.warn("Intent classifier '" + classifier.classifierName() + "' classify exception.", e);
            }
            if (intents == null || intents.isEmpty()) {
                continue;
            }
            if (!caches.isEmpty()) {
                List<String> values = intents.stream()
                        .map(Intent::getLabel)
                        .collect(Collectors.toList());
                caches.stream()
                        .filter(c -> c.getClass() != classifier.getClass())
                        .forEach(c -> c.set(text, values));
            }
            if (selfLearning && !(classifier instanceof IntentCache)) {
                List<TextLabel> trainingData = intents.stream()
                        .filter(i -> i.getScore() >= selfLearningThreshold)
                        .filter(i -> !selfLearningExcludes.contains(i.getLabel()))
                        .map(i -> new TextLabel(text, i.getLabel()))
                        .toList();
                if (!trainingData.isEmpty()) {
                    train(trainingData);
                }
            }
            return new CascadeResult(traceId, text, intents, cascadePath, start);
        }
        return new CascadeResult(traceId, text, cascadePath, start);
    }

    public Map<String, List<String>> train(@NonNull List<TextLabel> textLabels) {
        return trainers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().train(textLabels)));
    }

    public void remove(@NonNull Map<String, List<String>> keysMap) {
        keysMap.entrySet().stream()
                .filter(e -> trainers.containsKey(e.getKey()))
                .forEach(e -> trainers.get(e.getKey()).remove(e.getValue()));
    }
}
