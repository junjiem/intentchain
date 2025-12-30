package ai.intentchain.core.classifiers;

import ai.intentchain.core.classifiers.data.TextLabel;
import lombok.NonNull;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Collection;
import java.util.List;

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

    default List<String> train(@NonNull List<TextLabel> textLabels) {
        List<String> ids = textLabels.stream().map(t -> DigestUtils.md5Hex(t.getText())).toList();
        remove(ids);
        train(ids, textLabels);
        return ids;
    }

    List<String> train(@NonNull List<String> ids, @NonNull List<TextLabel> textLabels);

    void remove(@NonNull Collection<String> keys);
}
