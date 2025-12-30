package ai.intentchain.core.classifiers.data;

import lombok.*;

/**
 * Training Data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingData {
    @NonNull
    private String text;
    @NonNull
    private String label;
}
