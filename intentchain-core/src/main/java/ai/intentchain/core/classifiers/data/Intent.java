package ai.intentchain.core.classifiers.data;

import lombok.Getter;
import lombok.NonNull;

/**
 * Intent
 */
@Getter
public class Intent {
    @NonNull
    private final String label;

    private double score = 1.0;

    private Intent(@NonNull String label) {
        this.label = label;
    }

    private Intent(@NonNull String label, double score) {
        this.label = label;
        this.score = score;
    }

    public static Intent from(@NonNull String label) {
        return new Intent(label);
    }

    public static Intent from(@NonNull String label, double score) {
        return new Intent(label, score);
    }
}
