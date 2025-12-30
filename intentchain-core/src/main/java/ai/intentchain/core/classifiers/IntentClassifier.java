package ai.intentchain.core.classifiers;

import ai.intentchain.core.classifiers.data.Intent;
import lombok.NonNull;

import java.util.List;

/**
 * Intent Classifier
 */
public interface IntentClassifier {

    String classifierName();

    /**
     * Classifies the given text and returns labels with scores.
     *
     * @param text Text to classify.
     * @return A list of labels with corresponding scores. Can contain zero, one, or multiple labels.
     */
    List<Intent> classify(@NonNull String text);
}
