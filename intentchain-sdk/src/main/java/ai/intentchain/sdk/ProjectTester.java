package ai.intentchain.sdk;

import ai.intentchain.core.chain.data.CascadeResult;
import ai.intentchain.core.classifiers.data.Intent;
import ai.intentchain.core.classifiers.data.TextLabel;
import ai.intentchain.sdk.data.TestCase;
import ai.intentchain.sdk.data.TestReport;
import ai.intentchain.sdk.utils.ProjectUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ProjectTester {

    private static final String TESTS_DIR_NAME = "tests";

    private final Path projectPath;
    private final ProjectRunner runner;

    public ProjectTester(@NonNull Path projectPath) {
        this.projectPath = projectPath;
        this.runner = new ProjectRunner(projectPath);
    }

    /**
     * Run tests and generate report
     *
     * @return test report
     * @throws IOException if reading test files fails
     */
    public TestReport test() throws IOException {
        log.info("Start testing project: {}", projectPath);
        TestReport report = new TestReport();

        Path testsDir = projectPath.resolve(TESTS_DIR_NAME);
        if (!Files.exists(testsDir)) {
            log.warn("Tests directory not found: {}", testsDir);
            throw new IOException("Tests directory not found: " + testsDir);
        }

        // Scan all CSV files in tests directory
        List<Path> csvFiles = ProjectUtil.scanCsvFiles(testsDir);
        log.info("Found {} CSV files in tests directory", csvFiles.size());

        if (csvFiles.isEmpty()) {
            log.warn("No CSV test files found in tests directory");
            throw new IOException("No CSV test files found in tests directory: " + testsDir);
        }

        AtomicBoolean isCompleted = new AtomicBoolean(false);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            if (!isCompleted.get()) System.out.print("█");
        }, 0, 3, TimeUnit.SECONDS); // Print the progress bar every 3 seconds
        try {
            // Process each CSV file
            for (Path csvFile : csvFiles) {
                log.info("Processing test file: {}", projectPath.relativize(csvFile));
                processTestFile(csvFile, report);
            }
        } catch (Exception e) {
            isCompleted.set(true);
            scheduler.shutdown();
            throw new RuntimeException("Processing test file failed", e);
        } finally {
            isCompleted.set(true);
            scheduler.shutdown();
        }

        report.finish();
        log.info("Testing completed. Total: {}, Correct: {}, Failed: {}, Accuracy: {:.2f}%",
                report.getTotalCases(), report.getCorrectCases(), report.getFailedCases(),
                report.getAccuracy() * 100);

        return report;
    }

    /**
     * Process a single test file
     *
     * @param csvFile test file path
     * @param report  test report
     * @throws IOException if reading file fails
     */
    private void processTestFile(@NonNull Path csvFile, @NonNull TestReport report) throws IOException {
        List<TextLabel> textLabels = ProjectUtil.loadTextLabels(csvFile, projectPath);

        for (TextLabel textLabel : textLabels) {
            TestCase testCase = new TestCase(textLabel.getText(), textLabel.getLabel());

            try {
                long startTime = System.currentTimeMillis();
                CascadeResult result = runner.classify(textLabel.getText());
                long endTime = System.currentTimeMillis();
                long durationMs = endTime - startTime;

                // Get the final predicted labels
                List<String> predictedLabels = result.getIntents().stream().map(Intent::getLabel).toList();

                // Compare with expected label
                boolean correct = !predictedLabels.isEmpty() && predictedLabels.get(0).equals(textLabel.getLabel());

                testCase.setPrediction(predictedLabels, correct, durationMs, result.getCascadePath());
                log.debug("Test case - Text: [{}], Expected: [{}], Predicted: [{}], Correct: {}",
                        textLabel.getText(), textLabel.getLabel(), predictedLabels, correct);
            } catch (Exception e) {
                log.error("Error classifying text: [{}]", textLabel.getText(), e);
                testCase.setPrediction(Collections.singletonList("ERROR: " + e.getMessage()),
                        false, 0, Collections.emptyList());
            }

            report.addTestCase(testCase);
        }
    }

    /**
     * Extract the final intent from cascade result
     *
     * @param result cascade result
     * @return final intent label
     */
    private String extractFinalIntent(@NonNull CascadeResult result) {
        List<Intent> intents = result.getIntents();
        if (intents.isEmpty()) {
            return "UNKNOWN";
        }
        // Return the last intent's label
        return intents.get(intents.size() - 1).getLabel();
    }
}
