package org.fakebelieve;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
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
    protected static Catalog catalog;
    protected static String catalogName;
    protected static Namespace currentSchema;

    public static void main(String[] args) throws Exception {
        Icebreaker cli = new Icebreaker();
        cli.run(args);
    }

    public void run(String[] args) throws Exception {
        AnsiConsole.systemInstall();

        Supplier<Path> workDir = () -> Paths.get(System.getProperty("user.dir"));
        Supplier<Path> homeDir = () -> Paths.get(System.getProperty("user.home"));

        CommandLine cmd = new CommandLine(this);
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
                    String line = reader.readLine(prompt());
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

    private static String prompt() {
        StringBuilder prompt = new StringBuilder("iceberg");
        if (catalogName != null) {
            prompt.append("[").append(catalogName);
            if (currentSchema != null) {
                prompt.append(".").append(currentSchema);
            }
            prompt.append("]");
        }
        prompt.append("> ");
        return prompt.toString();
    }

    protected static TableIdentifier tableIdentifier(String tableIdentifier) {
        TableIdentifier identifier = TableIdentifier.parse(tableIdentifier);
        if (!identifier.hasNamespace() && currentSchema != null) {
            identifier = TableIdentifier.of(currentSchema, identifier.name());
        }
        return identifier;
    }

    protected static boolean activeCatalog() {
        if (catalog == null) {
            System.err.println("No catalog configured. Use 'catalog' command first.");
            return false;
        }
        return true;
    }
}
