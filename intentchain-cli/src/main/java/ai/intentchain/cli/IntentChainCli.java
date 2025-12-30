package ai.intentchain.cli;

import ai.intentchain.cli.commands.*;
import ai.intentchain.cli.provider.VersionProvider;
import ai.intentchain.cli.utils.AnsiUtil;
import lombok.extern.slf4j.Slf4j;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Callable;


@Slf4j
@Command(
        name = "IntentChain",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "IntentChain Command Line Interface",
        subcommands = {
                InitCommand.class,
                BuildCommand.class,
                RunCommand.class,
                CleanCommand.class,
                ServerCommand.class
        }
)
public class IntentChainCli implements Callable<Integer> {

    private final static String[] GRADIENT_COLORS = {
            "0;3;1", "0;4;1", "0;5;1", "0;5;2", "0;5;3", "0;5;4",
            "0;5;5", "0;4;4", "0;4;3", "0;4;2", "0;3;3", "0;3;2",
    };

    @Override
    public Integer call() {
        System.out.println("IntentChain - Cascaded intent classifier");
        System.out.println("Use 'ichain --help' show available commands");
        return 0;
    }

    private static void printBanner() {
        try (InputStream is = IntentChainCli.class.getResourceAsStream("/banner.txt")) {
            if (is != null) {
                List<String> lines = new BufferedReader(new InputStreamReader(is)).lines().toList();
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).isBlank()) continue;
                    String color = GRADIENT_COLORS[i % GRADIENT_COLORS.length];
                    System.out.println(AnsiUtil.string(
                            "@|bold,fg(" + color + ") " + lines.get(i) + "|@"));
                }
            }
        } catch (IOException e) {
            //
        }
    }

    public static void main(String[] args) {
        // 检测系统编码
        String osName = System.getProperty("os.name").toLowerCase();
        String systemEncoding = Charset.defaultCharset().displayName();
        log.info("OS: {}, System encoding: {}", osName, systemEncoding);

        printBanner();
        AnsiConsole.systemInstall(); // enable colors on Windows
        int exitCode = new CommandLine(new IntentChainCli()).execute(args);
        AnsiConsole.systemUninstall(); // cleanup when done
        System.exit(exitCode);
    }
}