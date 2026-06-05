package org.fakebelieve;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.Table;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.SupportsStorageCredentials;
import picocli.CommandLine;

@CommandLine.Command(
        name = "unsafe",
        description = "Unsafe management commands",
        mixinStandardHelpOptions = true,
        subcommands = {
            UnsafeCommands.UnsafeAppendDataFileCommand.class,
            UnsafeCommands.UnsafeWriteParquetCommand.class,
            UnsafeCommands.ShowCredentialsCommand.class,
        })
class UnsafeCommands implements Runnable {
    @Override
    public void run() {
        System.out.println("Use --help to see available unsafe commands");
    }

    @CommandLine.Command(name = "append-data-file", description = "Append a Parquet data file to an Iceberg table")
    static class UnsafeAppendDataFileCommand implements Runnable {
        private final IcebreakerContext context;

        public UnsafeAppendDataFileCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Table identifier (e.g., default.my_table)")
        String tableIdentifier;

        @CommandLine.Parameters(description = "Parquet file path to append")
        String parquetFilePath;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            TableIdentifier identifier = context.tableIdentifier(tableIdentifier);

            try {
                Table table = context.getCatalog().loadTable(identifier);

                try (FileIO io = table.io()) {
                    // Get file size using table.io()
                    long fileSize = io.newInputFile(parquetFilePath).getLength();
                    long recordCount = DataUtil.getParquetRowCount(table, parquetFilePath);

                    System.out.println("File size is : " + fileSize + " bytes, record count is: " + recordCount);

                    DataFile dataFile = DataFiles.builder(table.spec())
                            .withPath(parquetFilePath)
                            .withFileSizeInBytes(fileSize)
                            .withRecordCount(recordCount)
                            .build();

                    table.newAppend().appendFile(dataFile).commit();

                    System.out.println("Successfully appended file to table: " + table.name());
                }
            } catch (Exception e) {
                System.err.println("Failed to append file to table '" + identifier + "': " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(
            name = "write-parquet",
            description = "Write data to a Parquet file and commit to an existing Iceberg table")
    static class UnsafeWriteParquetCommand implements Runnable {
        private final IcebreakerContext context;

        public UnsafeWriteParquetCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Table identifier (e.g., default.my_table)")
        String tableIdentifier;

        @CommandLine.Option(
                names = {"-d", "--data"},
                description = "JSON data to write (array format)",
                required = true)
        String jsonData;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            TableIdentifier identifier = context.tableIdentifier(tableIdentifier);

            try {
                Table table = context.getCatalog().loadTable(identifier);

                // Parse JSON data into records
                List<Record> records = DataUtil.parseJsonData(jsonData, table.schema());

                // Write data using Iceberg's data writer
                DataFile dataFile = DataUtil.writeDataToParquet(table, records);

                // Commit the data file to the table
                table.newAppend().appendFile(dataFile).commit();

                System.out.println("Successfully wrote " + records.size() + " records to table: " + table.name());
                System.out.println("Data file location: " + dataFile.location());
                System.out.println("File size: " + dataFile.fileSizeInBytes() + " bytes");

            } catch (Exception e) {
                System.err.println("Failed to write data to table '" + identifier + "': " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(
            name = "credentials",
            description = "Show credentials for accessing an Iceberg table and storage")
    static class ShowCredentialsCommand implements Runnable {
        private final IcebreakerContext context;

        public ShowCredentialsCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Table identifier (e.g., default.my_table)")
        String tableIdentifier;

        @CommandLine.Option(
                names = {"-v", "--verbose"},
                description = "Show detailed credential information")
        boolean verbose;

        @CommandLine.Option(
                names = {"-u", "--unmask"},
                description = "Unmask sensitive credential values",
                defaultValue = "false")
        boolean unmaskSensitive;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            TableIdentifier identifier = context.tableIdentifier(tableIdentifier);

            try {
                Table table = context.getCatalog().loadTable(identifier);

                try (FileIO io = table.io()) {

                    Map<String, String> properties = io.properties();

                    System.out.println("Credentials for table '" + identifier + "':");

                    // Common credential property patterns
                    String[] credentialKeys = {
                        S3FileIOProperties.ACCESS_KEY_ID,
                        S3FileIOProperties.SECRET_ACCESS_KEY,
                        S3FileIOProperties.SESSION_TOKEN,
                        S3FileIOProperties.ENDPOINT,
                        "s3.session-token-expires-at-ms",
                        GCPProperties.GCS_PROJECT_ID,
                        GCPProperties.GCS_OAUTH2_TOKEN,
                        GCPProperties.GCS_OAUTH2_TOKEN_EXPIRES_AT,
                        // TODO: Add Azure and other cloud provider keys as needed
                    };

                    boolean hasCredentials = false;

                    for (String key : credentialKeys) {
                        if (properties.containsKey(key)) {
                            hasCredentials = true;
                            String value = properties.get(key);

                            if (!unmaskSensitive && isSensitiveKey(key)) {
                                value = maskValue(value);
                            }

                            System.out.printf("  %s: %s%n", key, value);
                        }
                    }

                    // Show additional credential-related properties if verbose
                    if (verbose) {
                        if (io instanceof SupportsStorageCredentials credIO) {

                            String credentials = new ObjectMapper()
                                    .writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(credIO.credentials());
                            System.out.println("\nStorage credentials from IO:");
                            System.out.println(credentials);
                        }

                        System.out.println("\nAll credential-related properties:");
                        properties.entrySet().stream()
                                .filter(entry -> isCredentialRelated(entry.getKey()))
                                .forEach(entry -> {
                                    String value = entry.getValue();
                                    if (!unmaskSensitive && isSensitiveKey(entry.getKey())) {
                                        value = maskValue(value);
                                    }
                                    System.out.printf("  %s: %s%n", entry.getKey(), value);
                                });
                    }

                    if (!hasCredentials && !verbose) {
                        System.out.println("  No standard credentials found in table properties");
                        System.out.println("  Use --verbose to see all credential-related properties");
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to show credentials for table '" + identifier + "': " + e.getMessage());
            }
        }

        private boolean isSensitiveKey(String key) {
            String lowerKey = key.toLowerCase();
            return lowerKey.contains("secret")
                    || lowerKey.contains("key")
                    || lowerKey.contains("token")
                    || lowerKey.contains("password");
        }

        private boolean isCredentialRelated(String key) {
            String lowerKey = key.toLowerCase();
            return lowerKey.contains("s3")
                    || lowerKey.contains("gcs")
                    || lowerKey.contains("azure")
                    || lowerKey.contains("credential")
                    || lowerKey.contains("auth")
                    || lowerKey.contains("security")
                    || lowerKey.contains("access")
                    || lowerKey.contains("secret")
                    || lowerKey.contains("token");
        }

        private String maskValue(String value) {
            if (value == null || value.length() <= 4) {
                return "****";
            }
            return value.substring(0, 4) + "****" + value.substring(value.length() - 2);
        }
    }
}
