package ai.intentchain.core.classifiers;

import ai.intentchain.core.classifiers.data.TrainingData;
import lombok.NonNull;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Intent Trainer
 */
public interface IntentTrainer {
    /**
     * Whether the trainer uses the persistent storage mode.
     */
    default boolean isPersistent() {
        return true;
    }

    default List<String> train(@NonNull List<TrainingData> trainingData) {
        List<String> ids = trainingData.stream().map(t -> DigestUtils.md5Hex(t.getText())).toList();
        remove(ids);
        train(ids, trainingData);
        return ids;
    }

    List<String> train(@NonNull List<String> ids, @NonNull List<TrainingData> trainingData);

    void remove(@NonNull Collection<String> keys);
}
