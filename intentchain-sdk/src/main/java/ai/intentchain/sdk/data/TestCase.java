package ai.intentchain.sdk.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;

/**
 * Test case data
 */
@Getter
@AllArgsConstructor
public class TestCase {
    /**
     * Input text
     */
    @NonNull
    private final String text;

    /**
     * Expected label
     */
    @NonNull
    private final String expectedLabel;

    /**
     * Predicted labels
     */
    private List<String> predictedLabels;

    /**
     * Whether the prediction is correct
     */
    private boolean correct;

    /**
     * Time taken for classification (in milliseconds)
     */
    private long durationMs;

    /**
     * Cascade path
     */
    private List<String> cascadePath;

    public TestCase(@NonNull String text, @NonNull String expectedLabel) {
        this.text = text;
        this.expectedLabel = expectedLabel;
    }

    public void setPrediction(@NonNull List<String> predictedLabels, boolean correct,
                            long durationMs, @NonNull List<String> cascadePath) {
        this.predictedLabels = predictedLabels;
        this.correct = correct;
        this.durationMs = durationMs;
        this.cascadePath = cascadePath;
    }
}
