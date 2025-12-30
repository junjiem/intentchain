package ai.intentchain.cli.commands;

import ai.intentchain.cli.provider.VersionProvider;
import ai.intentchain.cli.utils.AnsiUtil;
import ai.intentchain.sdk.ProjectBuilder;
import ai.intentchain.sdk.ProjectTester;
import ai.intentchain.sdk.data.TestReport;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Test project commands
 */
@Command(
        name = "test",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Test IntentChain project with test dataset"
)
@Slf4j
public class TestCommand implements Callable<Integer> {

    @Option(names = {"-p", "--project-path"},
            description = "Project path (default: current directory)",
            defaultValue = ".")
    private String projectPath;

    @Option(names = {"-o", "--output"},
            description = "Output report file path (optional)")
    private String outputPath;

    @Option(names = {"--skip-build"},
            description = "Skip incremental build")
    private boolean skipBuild;

    @Override
    public Integer call() {
        try {
            Path path = Paths.get(projectPath).toAbsolutePath();
            log.info("Start testing the project: {}", path);
            System.out.println("📁 Project path: " + path);

            // Build project first unless skipped
            if (!skipBuild) {
                System.out.println("⚒️ Start incremental build the project");
                ProjectBuilder builder = new ProjectBuilder(path);
                try {
                    builder.build();
                    System.out.println(AnsiUtil.string(
                            "@|fg(green) ✅ Incremental build completed|@"));
                } catch (IOException e) {
                    throw new RuntimeException("The project incremental build failed", e);
                }
            } else {
                log.info("Skipping incremental build");
                System.out.println("⏭️ Skipping incremental build");
            }

            // Run tests
            System.out.println("🧪 Start running tests...");
            ProjectTester tester = new ProjectTester(path);
            TestReport report = tester.test();

            // Display report
            String reportContent = report.generateReport();
            System.out.println(reportContent);

            // Save report to file if output path is specified
            if (outputPath != null && !outputPath.trim().isEmpty()) {
                Path outputFilePath = Paths.get(outputPath).toAbsolutePath();
                Files.writeString(outputFilePath, reportContent);
                System.out.println(AnsiUtil.string(
                        "@|fg(green) 💾 Report saved to: " + outputFilePath + "|@"));
                log.info("Report saved to: {}", outputFilePath);
            }

            // Print summary
            if (report.getAccuracy() >= 0.95) {
                System.out.println(AnsiUtil.string(
                        "@|fg(green),bold ✅ Testing completed with excellent accuracy!|@"));
            } else if (report.getAccuracy() >= 0.80) {
                System.out.println(AnsiUtil.string(
                        "@|fg(yellow),bold ⚠️ Testing completed with moderate accuracy.|@"));
            } else {
                System.out.println(AnsiUtil.string(
                        "@|fg(red),bold ❌ Testing completed with low accuracy. Please review the model.|@"));
            }

            return 0;
        } catch (Exception e) {
            log.error("Project test failed", e);
            System.err.println(AnsiUtil.string(
                    "@|fg(red) ❌ Test failed: " + e.getMessage() + "|@"));
            return 1;
        }
    }
}
