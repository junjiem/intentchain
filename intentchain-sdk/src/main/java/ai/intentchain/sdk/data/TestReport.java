package ai.intentchain.sdk.data;

import lombok.Getter;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test report data
 */
@Getter
public class TestReport {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Test start time
     */
    @NonNull
    private final LocalDateTime startTime;

    /**
     * Test end time
     */
    private LocalDateTime endTime;

    /**
     * Total test cases
     */
    private int totalCases = 0;

    /**
     * Correct predictions
     */
    private int correctCases = 0;

    /**
     * Failed predictions
     */
    private int failedCases = 0;

    /**
     * Test cases list
     */
    @NonNull
    private final List<TestCase> testCases = new ArrayList<>();

    /**
     * Per-label statistics
     */
    @NonNull
    private final Map<String, LabelStats> labelStatsMap = new HashMap<>();

    public TestReport() {
        this.startTime = LocalDateTime.now();
    }

    public void addTestCase(@NonNull TestCase testCase) {
        testCases.add(testCase);
        totalCases++;
        if (testCase.isCorrect()) {
            correctCases++;
        } else {
            failedCases++;
        }

        // Update label statistics
        String label = testCase.getExpectedLabel();
        LabelStats stats = labelStatsMap.computeIfAbsent(label, k -> new LabelStats(label));
        stats.incrementTotal();
        if (testCase.isCorrect()) {
            stats.incrementCorrect();
        }
    }

    public void finish() {
        this.endTime = LocalDateTime.now();
    }

    /**
     * Get accuracy rate
     */
    public double getAccuracy() {
        if (totalCases == 0) {
            return 0.0;
        }
        return (double) correctCases / totalCases;
    }

    /**
     * Get total duration in milliseconds
     */
    public long getTotalDurationMs() {
        return testCases.stream().mapToLong(TestCase::getDurationMs).sum();
    }

    /**
     * Get average duration in milliseconds
     */
    public double getAverageDurationMs() {
        if (totalCases == 0) {
            return 0.0;
        }
        return (double) getTotalDurationMs() / totalCases;
    }

    /**
     * Generate report content
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(80)).append("\n");
        sb.append("                        IntentChain Test Report\n");
        sb.append("=".repeat(80)).append("\n\n");

        sb.append("Test Time: ").append(startTime.format(FORMATTER));
        if (endTime != null) {
            sb.append(" - ").append(endTime.format(FORMATTER));
        }
        sb.append("\n\n");

        sb.append("-".repeat(80)).append("\n");
        sb.append("Overall Statistics\n");
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("Total Cases:      %d\n", totalCases));
        sb.append(String.format("Correct Cases:    %d\n", correctCases));
        sb.append(String.format("Failed Cases:     %d\n", failedCases));
        sb.append(String.format("Accuracy:         %.2f%%\n", getAccuracy() * 100));
        sb.append(String.format("Total Duration:   %.2f s\n", getTotalDurationMs() / 1000.0));
        sb.append(String.format("Average Duration: %.3f s\n\n", getAverageDurationMs() / 1000.0));

        if (!labelStatsMap.isEmpty()) {
            sb.append("-".repeat(80)).append("\n");
            sb.append("Per-Label Statistics\n");
            sb.append("-".repeat(80)).append("\n");
            sb.append(String.format("%-30s %10s %10s %10s\n", "Label", "Total", "Correct", "Accuracy"));
            sb.append("-".repeat(80)).append("\n");
            labelStatsMap.values().stream()
                    .sorted((a, b) -> b.getTotal() - a.getTotal())
                    .forEach(stats -> {
                        sb.append(String.format("%-30s %10d %10d %9.2f%%\n",
                                stats.getLabel(),
                                stats.getTotal(),
                                stats.getCorrect(),
                                stats.getAccuracy() * 100));
                    });
            sb.append("\n");
        }

        // Failed cases details
        List<TestCase> failedTestCases = testCases.stream()
                .filter(tc -> !tc.isCorrect())
                .toList();
        if (!failedTestCases.isEmpty()) {
            sb.append("-".repeat(80)).append("\n");
            sb.append("Failed Cases Details\n");
            sb.append("-".repeat(80)).append("\n");
            for (int i = 0; i < failedTestCases.size(); i++) {
                TestCase tc = failedTestCases.get(i);
                sb.append(String.format("\n[%d] Text: %s\n", i + 1, tc.getText()));
                sb.append(String.format("    Expected:  %s\n", tc.getExpectedLabel()));
                sb.append(String.format("    Predicted: %s\n", tc.getPredictedLabels().isEmpty()
                        ? "N/A" : String.join(", ", tc.getPredictedLabels())));
                sb.append(String.format("    Cascade:   %s\n", tc.getCascadePath().isEmpty()
                        ? "N/A" : String.join(" -> ", tc.getCascadePath())));
            }
            sb.append("\n");
        }

        sb.append("=".repeat(80)).append("\n");
        return sb.toString();
    }

    /**
     * Label statistics
     */
    @Getter
    public static class LabelStats {
        private final String label;
        private int total = 0;
        private int correct = 0;

        public LabelStats(String label) {
            this.label = label;
        }

        public void incrementTotal() {
            total++;
        }

        public void incrementCorrect() {
            correct++;
        }

        public double getAccuracy() {
            if (total == 0) {
                return 0.0;
            }
            return (double) correct / total;
        }
    }
}
