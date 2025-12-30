package ai.intentchain.core.classifiers.data;

import lombok.*;

/**
 * Text And Label
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextLabel {
    @NonNull
    private String text;
    @NonNull
    private String label;
}
