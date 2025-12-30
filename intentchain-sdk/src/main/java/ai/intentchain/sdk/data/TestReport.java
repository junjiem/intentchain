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
    }

    public void finish() {
        this.endTime = LocalDateTime.now();
        // Calculate metrics for each label
        calculateMetrics();
    }

    /**
     * Calculate precision, recall, and F1 metrics for each label
     */
    private void calculateMetrics() {
        // Collect all unique labels (from both expected and predicted)
        for (TestCase testCase : testCases) {
            String expectedLabel = testCase.getExpectedLabel();
            labelStatsMap.computeIfAbsent(expectedLabel, k -> new LabelStats(expectedLabel));
            
            // Also track predicted labels
            for (String predictedLabel : testCase.getPredictedLabels()) {
                labelStatsMap.computeIfAbsent(predictedLabel, k -> new LabelStats(predictedLabel));
            }
        }

        // Calculate TP, FP, FN for each label
        for (TestCase testCase : testCases) {
            String expectedLabel = testCase.getExpectedLabel();
            List<String> predictedLabels = testCase.getPredictedLabels();

            for (Map.Entry<String, LabelStats> entry : labelStatsMap.entrySet()) {
                String label = entry.getKey();
                LabelStats stats = entry.getValue();

                stats.incrementTotal();
                if (testCase.isCorrect()) {
                    stats.incrementCorrect();
                }

                boolean isExpected = label.equals(expectedLabel);
                boolean isPredicted = predictedLabels.contains(label);

                if (isExpected && isPredicted) {
                    stats.incrementTP(); // True Positive
                } else if (!isExpected && isPredicted) {
                    stats.incrementFP(); // False Positive
                } else if (isExpected && !isPredicted) {
                    stats.incrementFN(); // False Negative
                }
                // TN (True Negative) is not needed for precision/recall/F1
            }
        }
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
     * Get macro-averaged precision
     */
    public double getMacroPrecision() {
        if (labelStatsMap.isEmpty()) {
            return 0.0;
        }
        return labelStatsMap.values().stream()
                .mapToDouble(LabelStats::getPrecision)
                .average()
                .orElse(0.0);
    }

    /**
     * Get macro-averaged recall
     */
    public double getMacroRecall() {
        if (labelStatsMap.isEmpty()) {
            return 0.0;
        }
        return labelStatsMap.values().stream()
                .mapToDouble(LabelStats::getRecall)
                .average()
                .orElse(0.0);
    }

    /**
     * Get macro-averaged F1 score
     */
    public double getMacroF1() {
        if (labelStatsMap.isEmpty()) {
            return 0.0;
        }
        return labelStatsMap.values().stream()
                .mapToDouble(LabelStats::getF1)
                .average()
                .orElse(0.0);
    }

    /**
     * Get weighted-averaged precision
     */
    public double getWeightedPrecision() {
        if (labelStatsMap.isEmpty() || totalCases == 0) {
            return 0.0;
        }
        return labelStatsMap.values().stream()
                .mapToDouble(stats -> stats.getPrecision() * stats.getSupport())
                .sum() / totalCases;
    }

    /**
     * Get weighted-averaged recall
     */
    public double getWeightedRecall() {
        if (labelStatsMap.isEmpty() || totalCases == 0) {
            return 0.0;
        }
        return labelStatsMap.values().stream()
                .mapToDouble(stats -> stats.getRecall() * stats.getSupport())
                .sum() / totalCases;
    }

    /**
     * Get weighted-averaged F1 score
     */
    public double getWeightedF1() {
        if (labelStatsMap.isEmpty() || totalCases == 0) {
            return 0.0;
        }
        return labelStatsMap.values().stream()
                .mapToDouble(stats -> stats.getF1() * stats.getSupport())
                .sum() / totalCases;
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
        sb.append(String.format("Total Cases:         %d\n", totalCases));
        sb.append(String.format("Correct Cases:       %d\n", correctCases));
        sb.append(String.format("Failed Cases:        %d\n", failedCases));
        sb.append(String.format("Accuracy:            %.2f%%\n\n", getAccuracy() * 100));
        
        sb.append(String.format("Macro Avg Precision: %.2f%%\n", getMacroPrecision() * 100));
        sb.append(String.format("Macro Avg Recall:    %.2f%%\n", getMacroRecall() * 100));
        sb.append(String.format("Macro Avg F1:        %.2f%%\n\n", getMacroF1() * 100));
        
        sb.append(String.format("Weighted Precision:  %.2f%%\n", getWeightedPrecision() * 100));
        sb.append(String.format("Weighted Recall:     %.2f%%\n", getWeightedRecall() * 100));
        sb.append(String.format("Weighted F1:         %.2f%%\n\n", getWeightedF1() * 100));
        
        sb.append(String.format("Total Duration:      %.2f s\n", getTotalDurationMs() / 1000.0));
        sb.append(String.format("Average Duration:    %.3f s\n\n", getAverageDurationMs() / 1000.0));

        if (!labelStatsMap.isEmpty()) {
            sb.append("-".repeat(80)).append("\n");
            sb.append("Per-Label Statistics\n");
            sb.append("-".repeat(80)).append("\n");
            sb.append(String.format("%-20s %8s %8s %9s %8s %9s %9s %9s\n",
                    "Label", "Total", "Correct", "Accuracy", "Support", "Precision", "Recall", "F1"));
            sb.append("-".repeat(80)).append("\n");
            labelStatsMap.values().stream()
                    .sorted((a, b) -> b.getSupport() - a.getSupport())
                    .forEach(stats -> {
                        sb.append(String.format("%-20s %8s %8s %8.2f%% %8d %8.2f%% %8.2f%% %8.2f%%\n",
                                stats.getLabel(),
                                stats.getTotal(),
                                stats.getCorrect(),
                                stats.getAccuracy() * 100,
                                stats.getSupport(),
                                stats.getPrecision() * 100,
                                stats.getRecall() * 100,
                                stats.getF1() * 100));
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
        private int truePositives = 0;  // TP: predicted as this label and actually is this label
        private int falsePositives = 0; // FP: predicted as this label but actually is not
        private int falseNegatives = 0; // FN: not predicted as this label but actually is this label

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

        public void incrementTP() {
            truePositives++;
        }

        public void incrementFP() {
            falsePositives++;
        }

        public void incrementFN() {
            falseNegatives++;
        }

        /**
         * Get support (number of true instances for this label)
         */
        public int getSupport() {
            return truePositives + falseNegatives;
        }

        /**
         * Get precision: TP / (TP + FP)
         */
        public double getPrecision() {
            int denominator = truePositives + falsePositives;
            if (denominator == 0) {
                return 0.0;
            }
            return (double) truePositives / denominator;
        }

        /**
         * Get recall: TP / (TP + FN)
         */
        public double getRecall() {
            int denominator = truePositives + falseNegatives;
            if (denominator == 0) {
                return 0.0;
            }
            return (double) truePositives / denominator;
        }

        /**
         * Get F1 score: 2 * (Precision * Recall) / (Precision + Recall)
         */
        public double getF1() {
            double precision = getPrecision();
            double recall = getRecall();
            double sum = precision + recall;
            if (sum == 0.0) {
                return 0.0;
            }
            return 2 * (precision * recall) / sum;
        }
    }
}
