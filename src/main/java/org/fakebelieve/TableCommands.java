package org.fakebelieve;

import static org.fakebelieve.OutputUtil.iprintf;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.iceberg.BlobMetadata;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.StatisticsFile;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.io.CloseableIterable;
import picocli.CommandLine;

public class TableCommands {
    @CommandLine.Command(name = "table", description = "Load an Iceberg table")
    static class LoadTableCommand implements Runnable {
        private final IcebreakerContext context;

        public LoadTableCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Table identifier (e.g., default.my_table)")
        String tableIdentifier;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            TableIdentifier identifier = context.tableIdentifier(tableIdentifier);

            try {

                Table table = context.getCatalog().loadTable(identifier);

                System.out.println("Table: " + table.name());
                System.out.println("Location: " + table.location());
                if (table instanceof HasTableOperations tableOperations) {
                    int formatVersion = tableOperations.operations().current().formatVersion();
                    System.out.println("Format version: " + formatVersion);
                    tableOperations.operations().current().statisticsFiles();
                }
                Map<String, Object> snapshotMap = JsonUtil.snapshotToMap(table.currentSnapshot());
                System.out.println("Current snapshot: " + JsonUtil.prettyPrintAsString(snapshotMap));
                System.out.println("Schema: " + table.schema());
                System.out.println("Properties: " + JsonUtil.prettyPrintAsString(new TreeMap<>(table.properties())));
                System.out.println("Spec: " + table.spec());
            } catch (Exception e) {
                System.err.println("Failed to load table '" + identifier + "': " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "data-files", description = "List all data files in an Iceberg table")
    static class ListDataFilesCommand implements Runnable {
        private final IcebreakerContext context;

        public ListDataFilesCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Table identifier (e.g., default.my_table)")
        String tableIdentifier;

        @CommandLine.Option(
                names = {"-v", "--verbose"},
                description = "Show detailed datafile information")
        boolean verbose;

        @CommandLine.Option(
                names = {"-p", "--partition"},
                description = "Show partition information")
        boolean partition;

        @CommandLine.Option(
                names = {"-m", "--metrics"},
                description = "Show detailed metric information")
        boolean metrics;

        @CommandLine.Option(
                names = {"-s", "--snapshot"},
                description = "Snapshot ID (defaults to current snapshot)")
        Long snapshotId;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            TableIdentifier identifier = context.tableIdentifier(tableIdentifier);

            try {
                Table table = context.getCatalog().loadTable(identifier);

                // Use specified snapshot or current snapshot
                Snapshot snapshot = snapshotId != null ? table.snapshot(snapshotId) : table.currentSnapshot();

                if (snapshot == null) {
                    System.out.println("No snapshot found for table: " + identifier);
                    return;
                }

                System.out.println(
                        "Data files in table '" + identifier + "' (snapshot: " + snapshot.snapshotId() + "):");

                long totalFiles;
                long totalSize;
                long totalRecords;

                Set<String> uniqueDataFiles = new HashSet<>();

                try (CloseableIterable<FileScanTask> scanTasks =
                        table.newScan().useSnapshot(snapshot.snapshotId()).planFiles()) {

                    totalFiles = 0;
                    totalSize = 0;
                    totalRecords = 0;

                    for (FileScanTask scanTask : scanTasks) {
                        DataFile dataFile = scanTask.file();
                        totalFiles++;
                        totalSize += dataFile.fileSizeInBytes();
                        totalRecords += dataFile.recordCount();

                        System.out.printf(
                                "  %s (size: %d bytes, records: %d)%n",
                                dataFile.location(), dataFile.fileSizeInBytes(), dataFile.recordCount());

                        if (verbose) {
                            DataUtil.printDataFileMetrics(table, dataFile);
                        }

                        if (partition) {
                            DataUtil.printPartition(table, dataFile);
                        }

                        if (metrics) {
                            DataUtil.printParquetMetrics(table, dataFile);
                        }

                        uniqueDataFiles.add(dataFile.location());
                    }
                }

                System.out.println();
                System.out.println("Summary:");
                System.out.println("  Total files: " + totalFiles);
                System.out.println("  Total unique files: " + uniqueDataFiles.size());
                System.out.println("  Total size: " + totalSize + " bytes");
                System.out.println("  Total records: " + totalRecords);

            } catch (Exception e) {
                System.err.println("Failed to list data files for table '" + identifier + "': " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "stats-files", description = "List all stats files in an Iceberg table")
    static class ListStatsFilesCommand implements Runnable {
        private final IcebreakerContext context;

        public ListStatsFilesCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Table identifier (e.g., default.my_table)")
        String tableIdentifier;

        @CommandLine.Option(
                names = {"-v", "--verbose"},
                description = "Show detailed datafile information")
        boolean verbose;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            TableIdentifier identifier = context.tableIdentifier(tableIdentifier);

            try {
                Table table = context.getCatalog().loadTable(identifier);

                System.out.println("Stats files in table '" + identifier + "'");

                long totalFiles = 0;
                long totalSize = 0;
                long totalBlobs = 0;

                for (StatisticsFile statsFile : table.statisticsFiles()) {
                    totalFiles++;
                    totalSize += statsFile.fileSizeInBytes();
                    totalBlobs += statsFile.blobMetadata().size();

                    System.out.printf(
                            "  %s (size: %d bytes, blobs: %d)%n",
                            statsFile.path(),
                            statsFile.fileSizeInBytes(),
                            statsFile.blobMetadata().size());

                    if (verbose) {
                        for (BlobMetadata blobMetadata : statsFile.blobMetadata()) {
                            iprintf(2, "Type: %s, Fields: %s%n", blobMetadata.type(), blobMetadata.fields());
                        }
                    }
                }

                System.out.println();
                System.out.println("Summary:");
                System.out.println("  Total files: " + totalFiles);
                System.out.println("  Total size: " + totalSize + " bytes");
                System.out.println("  Total blobs: " + totalBlobs);

            } catch (Exception e) {
                System.err.println("Failed to list data files for table '" + identifier + "': " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "tables", description = "List all tables in the current schema")
    static class ListTablesCommand implements Runnable {
        private final IcebreakerContext context;

        public ListTablesCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Schema name (optional)", arity = "0..1")
        String schemaName;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            if (context.getCurrentSchema() == null && schemaName == null) {
                System.err.println("No schema selected. Use 'schema' command, or an explicit schema.");
                return;
            }

            Namespace schema = (schemaName != null) ? Namespace.of(schemaName) : context.getCurrentSchema();

            try {
                List<TableIdentifier> tables = context.getCatalog().listTables(schema);

                if (tables.isEmpty()) {
                    System.out.println("No tables found in schema '" + schema + "'");
                } else {
                    System.out.println("Tables in schema '" + schema + "':");
                    for (TableIdentifier table : tables) {
                        System.out.println("  " + table.name());
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to list tables: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "snapshots", description = "List all snapshots in an Iceberg table")
    static class ListSnapshotsCommand implements Runnable {
        private final IcebreakerContext context;

        public ListSnapshotsCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Table identifier (e.g., default.my_table)")
        String tableIdentifier;

        @CommandLine.Option(
                names = {"-v", "--verbose"},
                description = "Show detailed snapshot information")
        boolean verbose;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            TableIdentifier identifier = context.tableIdentifier(tableIdentifier);

            try {
                Table table = context.getCatalog().loadTable(identifier);
                Iterable<Snapshot> snapshots = table.snapshots();

                System.out.println("Snapshots in table '" + identifier + "':");

                boolean hasSnapshots = false;
                for (Snapshot snapshot : snapshots) {
                    hasSnapshots = true;
                    String current =
                            (snapshot.snapshotId() == table.currentSnapshot().snapshotId()) ? " (current)" : "";

                    if (verbose) {
                        iprintf(2, "Snapshot %d%s%n", snapshot.snapshotId(), current);
                        iprintf(
                                4,
                                "Timestamp: %d (%s)%n",
                                snapshot.timestampMillis(),
                                Instant.ofEpochMilli(snapshot.timestampMillis()));
                        iprintf(4, "Operation: %s%n", snapshot.operation());
                        iprintf(4, "Summary: %s%n", JsonUtil.prettyPrintAsString(new TreeMap<>(snapshot.summary())));
                        if (snapshot.parentId() != null) {
                            iprintf(4, "Parent: %d%n", snapshot.parentId());
                        }
                        System.out.println();
                    } else {
                        iprintf(
                                2,
                                "%d - %s (%s)%s%n",
                                snapshot.snapshotId(),
                                snapshot.operation(),
                                Instant.ofEpochMilli(snapshot.timestampMillis()),
                                current);
                    }
                }

                if (!hasSnapshots) {
                    System.out.println("  No snapshots found");
                }

            } catch (Exception e) {
                System.err.println("Failed to list snapshots for table '" + identifier + "': " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "snapshot", description = "Show details of a specific snapshot in an Iceberg table")
    static class SnapshotCommand implements Runnable {
        private final IcebreakerContext context;

        public SnapshotCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Table identifier (e.g., default.my_table)")
        String tableIdentifier;

        @CommandLine.Option(
                names = {"-v", "--verbose"},
                description = "Show detailed snapshot information")
        boolean verbose;

        @CommandLine.Option(
                names = {"-s", "--snapshot"},
                required = true,
                description = "Snapshot ID (defaults to current snapshot)")
        Long snapshotId;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            TableIdentifier identifier = context.tableIdentifier(tableIdentifier);

            try {
                Table table = context.getCatalog().loadTable(identifier);
                Snapshot snapshot = table.snapshot(snapshotId);

                if (snapshot != null) {

                    String current =
                            (snapshot.snapshotId() == table.currentSnapshot().snapshotId()) ? " (current)" : "";

                    if (verbose) {
                        iprintf(2, "Snapshot %d%s%n", snapshot.snapshotId(), current);
                        iprintf(
                                4,
                                "Timestamp: %d (%s)%n",
                                snapshot.timestampMillis(),
                                Instant.ofEpochMilli(snapshot.timestampMillis()));
                        iprintf(4, "Operation: %s%n", snapshot.operation());
                        iprintf(4, "Summary: %s%n", JsonUtil.prettyPrintAsString(new TreeMap<>(snapshot.summary())));
                        if (snapshot.parentId() != null) {
                            iprintf(4, "Parent: %d%n", snapshot.parentId());
                        }
                        System.out.println();

                        boolean hasDataFiles = false;
                        for (DataFile dataFile : snapshot.addedDataFiles(table.io())) {
                            if (!hasDataFiles) {
                                iprintf(2, "Added Data Files:%n");
                                hasDataFiles = true;
                            }
                            iprintf(
                                    4,
                                    "%s (size: %d bytes, records: %d)%n",
                                    dataFile.location(),
                                    dataFile.fileSizeInBytes(),
                                    dataFile.recordCount());
                        }

                        boolean hasDeleteFiles = false;
                        for (DeleteFile dataFile : snapshot.addedDeleteFiles(table.io())) {
                            if (!hasDeleteFiles) {
                                iprintf(2, "Added Delete Files:%n");
                                hasDeleteFiles = true;
                            }
                            iprintf(
                                    4,
                                    "%s (size: %d bytes, records: %d)%n",
                                    dataFile.location(),
                                    dataFile.fileSizeInBytes(),
                                    dataFile.recordCount());
                        }

                    } else {
                        iprintf(
                                2,
                                "%d - %s (%s)%s%n",
                                snapshot.snapshotId(),
                                snapshot.operation(),
                                Instant.ofEpochMilli(snapshot.timestampMillis()),
                                current);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to show snapshot for table '" + identifier + "': " + e.getMessage());
            }
        }
    }
}
