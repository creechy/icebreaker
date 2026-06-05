package org.fakebelieve;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;
import org.fusesource.jansi.AnsiConsole;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;

@CommandLine.Command(
        name = "icebreaker",
        description = "Icebreaker",
        subcommands = {
            CatalogCommands.CatalogCommand.class,
            TableCommands.LoadTableCommand.class,
            TableCommands.ListTablesCommand.class,
            TableCommands.ListDataFilesCommand.class,
            TableCommands.ListStatsFilesCommand.class,
            TableCommands.ListSnapshotsCommand.class,
            TableCommands.SnapshotCommand.class,
            SchemaCommands.ListSchemasCommand.class,
            SchemaCommands.UseSchemaCommand.class,
            UnsafeCommands.class,
            CommandLine.HelpCommand.class
        })
public class Icebreaker {

    public static void main(String[] args) throws Exception {
        Icebreaker cli = new Icebreaker();
        cli.run(args);
    }

    public void run(String[] args) throws Exception {
        AnsiConsole.systemInstall();

        IcebreakerContext context = new IcebreakerContext();

        Supplier<Path> workDir = () -> Paths.get(System.getProperty("user.dir"));
        Supplier<Path> homeDir = () -> Paths.get(System.getProperty("user.home"));

        CommandLine cmd = new CommandLine(this, new IcebreakerFactory(context));
        PicocliCommands picocliCommands = new PicocliCommands(cmd);

        try (Terminal terminal = TerminalBuilder.builder().build()) {

            SystemRegistry systemRegistry =
                    new SystemRegistryImpl(new DefaultParser(), terminal, () -> workDir.get(), null);

            systemRegistry.setCommandRegistries(picocliCommands);
            // This changes the format of the help command.
            systemRegistry.register("help", picocliCommands);

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(systemRegistry.completer())
                    .parser(new DefaultParser())
                    .variable(LineReader.LIST_MAX, 50)
                    .variable(LineReader.HISTORY_FILE, homeDir.get().resolve(".iceberg_cli_history"))
                    .variable(LineReader.HISTORY_SIZE, 500)
                    .build();

            System.out.println("Icebreaker - Type 'help' for available commands");

            while (true) {
                try {
                    systemRegistry.cleanUp();
                    String line = reader.readLine(prompt(context));
                    systemRegistry.execute(line);
                } catch (UserInterruptException e) {
                    // Ignore
                } catch (EndOfFileException e) {
                    return;
                } catch (Exception e) {
                    systemRegistry.trace(e);
                }
            }

        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    private static String prompt(IcebreakerContext context) {
        StringBuilder prompt = new StringBuilder("iceberg");
        if (context.getCatalogName() != null) {
            prompt.append("[").append(context.getCatalogName());
            if (context.getCurrentSchema() != null) {
                prompt.append(".").append(context.getCurrentSchema());
            }
            prompt.append("]");
        }
        prompt.append("> ");
        return prompt.toString();
    }
}
