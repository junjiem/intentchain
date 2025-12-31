package ai.intentchain.cli.commands;

import ai.intentchain.cli.processor.InputProcessor;
import ai.intentchain.cli.provider.VersionProvider;
import ai.intentchain.cli.utils.AnsiUtil;
import ai.intentchain.core.chain.data.CascadeResult;
import ai.intentchain.core.classifiers.data.TextLabel;
import ai.intentchain.sdk.ProjectBuilder;
import ai.intentchain.sdk.ProjectRunner;
import ai.intentchain.sdk.utils.ProjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Run project commands
 */
@Command(
        name = "run",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Run IntentChain project and start interactive classification"
)
@Slf4j
public class RunCommand implements Callable<Integer> {

    private final static ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final static String RUN_COMMAND_HISTORY = "run_command_history";

    @Option(names = {"-p", "--project-path"},
            description = "Project path (default: current directory)",
            defaultValue = ".")
    private String projectPath;

    @Option(names = {"--skip-build"},
            description = "Skip incremental build")
    private boolean skipBuild;

    @Option(names = {"--skip-feedback"},
            description = "Skip ask user for feedback")
    private boolean skipFeedback;

    @Override
    public Integer call() {
        Path path = Paths.get(projectPath).toAbsolutePath();
        log.info("Start running the project: {}", path);
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

        Path historyFilePath = path.resolve(ProjectUtil.INTENTCHAIN_DIR_NAME + "/" + RUN_COMMAND_HISTORY);
        try (InputProcessor processor = new InputProcessor(historyFilePath)) {
            ProjectRunner runner = new ProjectRunner(path);
            printHelp(); // 打印帮助信息
            int round = 1;
            while (true) {
                System.out.println(
                        AnsiUtil.string("@|fg(green) "
                                        + ("─".repeat(50)) + "|@ @|bold,fg(yellow) Round " + round
                                        + "|@ @|fg(green) " + ("─".repeat(50)) + "|@"));
                String question;
                try {
                    question = processor.readLine(AnsiUtil.string(
                            "@|fg(yellow) ❓ Please enter the question:|@ "));
                } catch (EndOfFileException e) {
                    // Ctrl+D (EOF) - 优雅退出
                    log.debug("EOF received (Ctrl+D)");
                    System.out.println("👋 Bye!");
                    break;
                } catch (UserInterruptException e) {
                    // Ctrl+C - 中断信号
                    log.debug("User interrupt received (Ctrl+C)");
                    System.out.println("👋 Bye!");
                    break;
                }
                if (question.isEmpty()) {
                    continue;
                }
                if ("quit".equalsIgnoreCase(question) || "exit".equalsIgnoreCase(question)) {
                    System.out.println("👋 Bye!");
                    break;
                }
                if ("clear".equalsIgnoreCase(question)) {
                    processor.clearScreen();
                    continue;
                }
                if ("help".equalsIgnoreCase(question)) {
                    printHelp();
                    continue;
                }
                try {
                    processor.startSpinner(); // 开始等待指示器
                    CascadeResult result = runner.classify(question);
                    processor.stopSpinner(); // 停止等待指示器
                    Duration duration = result.getDuration();
                    String seconds = duration.toMillis() > 0 ?
                            String.format("%.3f", duration.toMillis() / 1000.0) :
                            String.format("%.6f", duration.toNanos() / 1000_000_000.0);
                    System.out.println(AnsiUtil.string("@|fg(blue) Duration: " + seconds + "s|@"));
                    String cascadePath = result.getCascadePath().isEmpty() ? "N/A"
                            : String.join(" -> ", result.getCascadePath());
                    System.out.println(AnsiUtil.string("@|fg(blue) Cascade: " + cascadePath + "|@"));
                    System.out.println(AnsiUtil.string("@|fg(cyan) Intents: "
                                                       + JSON_MAPPER.writeValueAsString(result.getIntents()) + "|@"));

                    // Ask user for feedback
                    if (!skipFeedback) {
                        String likeInput = processor.readLine(AnsiUtil.string(
                                "@|fg(magenta) 👍 Satisfied with the result? (y/yes to like and train, others to skip):|@ "));
                        if (likeInput != null &&
                            (likeInput.equalsIgnoreCase("y") || likeInput.equalsIgnoreCase("yes"))) {
                            System.out.println(AnsiUtil.string("@|fg(green) ⚡ Start training...|@"));
                            try {
                                List<TextLabel> textLabels = result.getIntents()
                                        .stream().map(i -> new TextLabel(question, i.getLabel()))
                                        .collect(Collectors.toList());
                                runner.train(textLabels);
                                System.out.println(AnsiUtil.string("@|fg(green) ✅ Training completed!|@"));
                            } catch (Exception e1) {
                                log.warn("Training failed: " + e1.getMessage(), e1);
                                System.out.println(AnsiUtil.string(
                                        "@|fg(red) ❌ Training failed: " + e1.getMessage() + "|@"));
                            }
                        } else {
                            System.out.println(AnsiUtil.string("@|fg(yellow) ⏭️ Training skipped|@"));
                        }
                    }

                } catch (EndOfFileException | UserInterruptException e) {
                    // 用户中断，优雅退出
                    System.out.println("👋 Bye!");
                    return 0;
                } catch (Exception e) {
                    log.warn("error: " + e.getMessage(), e);
                    System.out.println(AnsiUtil.string("@|fg(red) error: " + e.getMessage() + "|@"));
                }
                round += 1;
            }
            System.out.println(AnsiUtil.string("@|fg(green) " + ("─".repeat(100)) + "|@"));
            return 0;
        } catch (Exception e) {
            log.error("Run project failed", e);
            System.err.println(AnsiUtil.string(
                    "@|fg(red) ❌ Run failed: " + e.getMessage() + "|@"));
            return 1;
        }
    }

    private void printHelp() {
        System.out.println();
        System.out.println(AnsiUtil.string("@|bold,fg(cyan) 📖 IntentChain interactive help|@"));
        System.out.println();
        System.out.println(AnsiUtil.string("@|bold Commands:|@"));
        System.out.println(AnsiUtil.string("  @|fg(green) help|@         - Show this help message"));
        System.out.println(AnsiUtil.string("  @|fg(green) clear|@        - Clear the screen"));
        System.out.println(AnsiUtil.string("  @|fg(green) quit/exit|@    - Exit the conversation"));
        System.out.println();
        System.out.println(AnsiUtil.string("@|bold Keyboard Shortcuts:|@"));
        System.out.println(AnsiUtil.string("  @|fg(yellow) ↑/↓|@         - Navigate command history"));
        System.out.println(AnsiUtil.string("  @|fg(yellow) Home/End|@    - Move to start/end of line"));
        System.out.println(AnsiUtil.string("  @|fg(yellow) Ctrl+A/E|@    - Move to start/end of line"));
        System.out.println(AnsiUtil.string("  @|fg(yellow) Ctrl+C|@      - Interrupt and exit (immediate)"));
        System.out.println(AnsiUtil.string("  @|fg(yellow) Ctrl+D|@      - EOF signal and exit (graceful)"));
        System.out.println(AnsiUtil.string("  @|fg(yellow) Ctrl+L|@      - Clear screen"));
        System.out.println();
    }
}