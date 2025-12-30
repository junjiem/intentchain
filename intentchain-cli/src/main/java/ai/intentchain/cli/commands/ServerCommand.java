package ai.intentchain.cli.commands;

import ai.intentchain.cli.commands.server.OpenApiServerCommand;
import ai.intentchain.cli.provider.VersionProvider;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Project Server commands
 */
@Command(
        name = "server",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Start and manage IntentChain project server",
        subcommands = {
                OpenApiServerCommand.class
        }
)
@Slf4j
public class ServerCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("🌐 IntentChain Server Management");
        System.out.println("Use 'ichain server --help' show available commands");
        return 0;
    }
}
