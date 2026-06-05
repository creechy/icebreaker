package org.fakebelieve;

import static org.apache.iceberg.TableProperties.WRITE_DATA_LOCATION;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.parquet.ParquetUtil;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

public class DataUtil {
    protected static List<org.apache.iceberg.data.Record> parseJsonData(String jsonData, Schema schema) {
        List<org.apache.iceberg.data.Record> records = new ArrayList<>();

        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // Parse JSON data as array of maps
            List<Map<String, Object>> jsonRecords = objectMapper.readValue(jsonData, new TypeReference<>() {});

            for (Map<String, Object> jsonRecord : jsonRecords) {
                Record record = GenericRecord.create(schema);

                for (Map.Entry<String, Object> entry : jsonRecord.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    // Set field based on schema type
                    Types.NestedField nestedField = schema.findField(key);
                    if (nestedField != null) {
                        Object typedValue = convertValue(value, nestedField.type());
                        record.setField(key, typedValue);
                    } else {
                        System.out.println("warning: Unknown field: " + key);
                    }
                }

                records.add(record);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON data: " + e.getMessage(), e);
        }

        return records;
    }

    protected static Object convertValue(Object value, Type type) {
        if (value == null) {
            return null;
        }

        switch (type.typeId()) {
            case LONG:
                return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
            case INTEGER:
                return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
            case DOUBLE:
                return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
            case FLOAT:
                return value instanceof Number ? ((Number) value).floatValue() : Float.parseFloat(value.toString());
            case BOOLEAN:
                return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
            case STRING:
            default:
                return value.toString();
        }
    }

    protected static DataFile writeDataToParquet(Table table, List<Record> records) throws IOException {
        // Create output file in table location

        String writeDataPath = table.properties().getOrDefault(WRITE_DATA_LOCATION, table.location() + "/data/");

        String randomPath = new RandomPathGenerator().generatePath();

        String fileName = randomPath + "/" + table.uuid() + "/orbon/" + UUID.randomUUID() + "-"
                + Long.toString(System.currentTimeMillis(), 16) + ".parquet";
        String filePath = writeDataPath + (writeDataPath.endsWith("/") ? "" : "/") + fileName;

        System.out.println("Writing data to Parquet file: " + filePath);

        try (FileIO io = table.io()) {

            OutputFile outputFile = io.newOutputFile(filePath);

            // Create file appender for writing Parquet data
            GenericAppenderFactory appenderFactory = new GenericAppenderFactory(table.schema());
            FileAppender<Record> appender = appenderFactory.newAppender(outputFile, FileFormat.PARQUET);

            try {
                appender.addAll(records);
            } finally {
                appender.close();
            }

            // Get file metrics
            Metrics metrics = appender.metrics();

            // Build DataFile metadata
            return DataFiles.builder(table.spec())
                    .withPath(filePath)
                    .withFileSizeInBytes(outputFile.toInputFile().getLength())
                    .withRecordCount(metrics.recordCount())
                    .withMetrics(metrics)
                    .build();
        }
    }

    protected static long getParquetRowCount(Table table, String filePath) throws Exception {
        try (FileIO io = table.io()) {
            InputFile inputFile = io.newInputFile(filePath);
            Metrics metrics = ParquetUtil.fileMetrics(inputFile, MetricsConfig.getDefault());
            return metrics.recordCount();
        }
    }

    protected static void printPartition(Table table, DataFile dataFile) {
        if (dataFile.partition() != null && !table.spec().fields().isEmpty()) {
            System.out.print("    Partition: ");
            for (int i = 0; i < table.spec().fields().size(); i++) {
                String fieldName = table.spec().fields().get(i).name();
                String transform = table.spec().fields().get(i).transform().toString();
                Object partitionValue = dataFile.partition().get(i, Object.class);
                System.out.print(fieldName + "(" + transform + ")=" + partitionValue);
                if (i < table.spec().fields().size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println();
        }
    }

    protected static void printDataFileMetrics(Table table, DataFile dataFile) {
        Map<Integer, Long> nullValueCounts = dataFile.nullValueCounts();
        Map<Integer, Long> nanValueCounts = dataFile.nanValueCounts();
        Map<Integer, ByteBuffer> lowerBounds = dataFile.lowerBounds();
        Map<Integer, ByteBuffer> upperBounds = dataFile.upperBounds();

        Set<Integer> columnIds = new HashSet<>();
        columnIds.addAll(nullValueCounts != null ? nullValueCounts.keySet() : Set.of());
        columnIds.addAll(nanValueCounts != null ? nanValueCounts.keySet() : Set.of());
        columnIds.addAll(lowerBounds != null ? lowerBounds.keySet() : Set.of());
        columnIds.addAll(upperBounds != null ? upperBounds.keySet() : Set.of());

        List<Integer> sortedColumnIds = columnIds.stream().sorted().toList();

        for (Integer columnId : sortedColumnIds) {
            String metricString = generateStats(
                    table,
                    columnId,
                    nullValueCounts != null ? nullValueCounts : Map.of(),
                    nanValueCounts != null ? nanValueCounts : Map.of(),
                    lowerBounds,
                    upperBounds);
            System.out.println("    File: " + metricString);
        }
    }

    protected static void printParquetMetrics(Table table, DataFile dataFile) {
        try (FileIO io = table.io()) {
            InputFile inputFile = io.newInputFile(dataFile.location());

            Metrics metrics = ParquetUtil.fileMetrics(inputFile, MetricsConfig.forTable(table));

            // Map<Integer, Long> valueCounts = metrics.valueCounts();
            Map<Integer, Long> nullValueCounts = metrics.nullValueCounts();
            Map<Integer, Long> nanValueCounts = metrics.nanValueCounts();
            Map<Integer, ByteBuffer> lowerBounds = metrics.lowerBounds();
            Map<Integer, ByteBuffer> upperBounds = metrics.upperBounds();

            Set<Integer> columnIds = new HashSet<>();
            // columnIds.addAll(valueCounts != null ? valueCounts.keySet() : Set.of());
            columnIds.addAll(nullValueCounts != null ? nullValueCounts.keySet() : Set.of());
            columnIds.addAll(nanValueCounts != null ? nanValueCounts.keySet() : Set.of());
            columnIds.addAll(lowerBounds != null ? lowerBounds.keySet() : Set.of());
            columnIds.addAll(upperBounds != null ? upperBounds.keySet() : Set.of());

            List<Integer> sortedColumnIds = columnIds.stream().sorted().toList();

            for (Integer columnId : sortedColumnIds) {
                String metricString = generateStats(
                        table,
                        columnId,
                        nullValueCounts != null ? nullValueCounts : Map.of(),
                        nanValueCounts != null ? nanValueCounts : Map.of(),
                        lowerBounds,
                        upperBounds);
                System.out.println("    Metric: " + metricString);
            }
        }
    }

    private static String generateStats(
            Table table,
            int columnId,
            Map<Integer, Long> nullValueCounts,
            Map<Integer, Long> nanValueCounts,
            Map<Integer, ByteBuffer> lowerBounds,
            Map<Integer, ByteBuffer> upperBounds) {
        // Get the column type from table schema
        String columnName = table.schema().findColumnName(columnId);
        Type columnType = table.schema().findType(columnId);

        Long nullValueCount = nullValueCounts.getOrDefault(columnId, 0L);
        Long nanValueCount = nanValueCounts.getOrDefault(columnId, 0L);

        Object convertedLowerValue = null;
        Object convertedUpperValue = null;

        if (lowerBounds != null) {
            ByteBuffer value = lowerBounds.get(columnId);
            convertedLowerValue = Conversions.fromByteBuffer(columnType, value);
        }
        if (upperBounds != null) {
            ByteBuffer value = upperBounds.get(columnId);

            convertedUpperValue = Conversions.fromByteBuffer(columnType, value);
        }

        return "Column " + columnName + "[" + columnId + "]: " + "null count = " + nullValueCount + ", nan count = "
                + nanValueCount + ", bounds = (" + convertedLowerValue + ", " + convertedUpperValue + ")";
    }
}
