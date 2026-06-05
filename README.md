# Icebreaker

An interactive command-line interface for exploring and managing Apache Iceberg tables.

## Features

- Interactive REPL with command history and auto-completion
- Support for REST catalog with OAuth2 authentication
- Explore table schemas, snapshots, and metadata
- List and inspect data files and statistics files
- Navigate between schemas and tables
- View detailed snapshot information

## Prerequisites

- Java 11 or higher
- Gradle (included via Gradle wrapper)

## Building

Build the project using Gradle:

```bash
./gradlew build
```

Create a fat JAR with all dependencies:

```bash
./gradlew fatJar
```

## Running

Start the interactive CLI:

```bash
java -jar build/libs/icebreaker-0.42.0-SNAPSHOT-all.jar
```

Or run directly with Gradle:

```bash
./gradlew run
```

## Quick Reference

| Command | Summary |
|---------|---------|
| `catalog -n <name> -u <uri>` | Connect to an Iceberg catalog with authentication options |
| `schemas` | List all available schemas in the current catalog |
| `schema <name>` | Switch to a specific schema for subsequent operations |
| `tables [schema]` | List all tables in the current or specified schema |
| `table <identifier>` | Display detailed information about a table (schema, properties, current snapshot) |
| `snapshots <identifier> [-v]` | List all snapshots for a table with optional detailed view |
| `snapshot <identifier> -s <id> [-v]` | Show details of a specific snapshot including added files |
| `data-files <identifier> [-v] [-p] [-m]` | List all data files in a table with size and record counts |
| `stats-files <identifier> [-v]` | List statistics files for a table with blob metadata |
| `help` | Display available commands and usage information |

## Commands

### Catalog Configuration

Configure a connection to an Iceberg catalog:

```
catalog -n <name> -u <uri> [OPTIONS]
```

Options:

- `-n, --name`: Catalog name (required)
- `-c, --class`: Catalog class (default: org.apache.iceberg.rest.RESTCatalog)
- `-u, --uri`: Catalog URI (required)
- `-w, --warehouse`: Warehouse location
- `-t, --token`: Authentication token
- `-x, --credential`: Authentication credential
- `-o, --oauth2-server-uri`: OAuth2 server URI
- `-s, --scope`: OAuth2 scope
- `-d, --no-exchange`: Disable token exchange
- `-h, --header`: Custom HTTP headers (format: header.name=value)

Example:
```
catalog -n my_catalog -u https://catalog.example.com -t my_token
```

### Schema Management

List all schemas:
```
schemas
```

Switch to a schema:
```
schema <schema_name>
```

### Table Operations

List tables in the current or specified schema:
```
tables [schema_name]
```

Load and display table information:
```
table <table_identifier>
```

### Snapshot Management

List all snapshots in a table:
```
snapshots <table_identifier> [-v]
```

Show details of a specific snapshot:
```
snapshot <table_identifier> -s <snapshot_id> [-v]
```

Options:

- `-v, --verbose`: Show detailed information

### Data Files

List all data files in a table:
```
data-files <table_identifier> [OPTIONS]
```

Options:

- `-v, --verbose`: Show detailed data file information
- `-p, --partition`: Show partition information
- `-m, --metrics`: Show detailed metric information
- `-s, --snapshot`: Specify snapshot ID (defaults to current)

### Statistics Files

List all statistics files in a table:
```
stats-files <table_identifier> [-v]
```

Options:

- `-v, --verbose`: Show detailed statistics file information

## Interactive Features

- Command history: Use up/down arrows to navigate through previous commands
- Tab completion: Press TAB to auto-complete commands
- History file: Command history is saved to `~/.iceberg_cli_history`
- Context-aware prompt: The prompt displays the current catalog and schema

Example prompt:
```
iceberg[my_catalog.default]>
```

## Configuration

The CLI uses Hadoop and AWS configurations for accessing data in cloud storage. Ensure your environment is configured with appropriate credentials:

- AWS: Set `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables
- GCP: Configure Google Cloud credentials

## Dependencies

Key dependencies:

- Apache Iceberg 1.10.0
- Picocli 4.7.7 (CLI framework)
- JLine 3.21.0 (interactive console)
- AWS SDK 2.37.5 (S3, KMS, STS)
- Hadoop 3.3.6

## Development

Run code formatting:
```bash
./gradlew spotlessApply
```

Check code formatting:
```bash
./gradlew spotlessCheck
```

Run tests:
```bash
./gradlew test
```

## License

This project uses Apache Iceberg, which is licensed under the Apache License 2.0.
