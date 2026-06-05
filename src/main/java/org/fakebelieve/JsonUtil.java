package org.fakebelieve;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.iceberg.Snapshot;

public class JsonUtil {

    private static ObjectMapper mapper = new ObjectMapper();

    public static String prettyPrintAsString(Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    public static Map<String, Object> snapshotToMap(Snapshot snapshot) {
        if (snapshot == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> snapshotInfo = new LinkedHashMap<>();
        snapshotInfo.put("id", snapshot.snapshotId());
        snapshotInfo.put("timestamp_ms", snapshot.timestampMillis());
        snapshotInfo.put("operation", snapshot.operation());
        snapshotInfo.put("summary", snapshot.summary());

        if (snapshot.manifestListLocation() != null) {
            snapshotInfo.put("manifest-list", snapshot.manifestListLocation());
        }

        if (snapshot.schemaId() != null) {
            snapshotInfo.put("schema-id", snapshot.schemaId());
        }

        return snapshotInfo;
    }
}
