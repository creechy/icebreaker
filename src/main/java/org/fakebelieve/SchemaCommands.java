package org.fakebelieve;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.rest.RESTCatalog;
import picocli.CommandLine;

public class SchemaCommands {
    @CommandLine.Command(
            name = "schemas",
            aliases = {"namespaces"},
            description = "List all schemas in the catalog")
    static class ListSchemasCommand implements Runnable {
        @Override
        public void run() {
            if (!Icebreaker.activeCatalog()) {
                return;
            }

            try {
                // List namespaces under the root namespace (empty namespace)
                List<Namespace> schemas = ((RESTCatalog) Icebreaker.catalog).listNamespaces(Namespace.empty());

                if (schemas.isEmpty()) {
                    System.out.println("No schemas found in catalog '" + Icebreaker.catalogName + "'");
                } else {
                    System.out.println("Schemas in catalog '" + Icebreaker.catalogName + "':");
                    for (Namespace namespace : schemas) {
                        System.out.println("  " + namespace);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to list schemas: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(
            name = "use",
            aliases = {"schema", "namespace"},
            description = "Set the current schema/namespace")
    static class UseSchemaCommand implements Runnable {
        @CommandLine.Parameters(description = "Schema name to use")
        String schemaName;

        @Override
        public void run() {
            if (!Icebreaker.activeCatalog()) {
                return;
            }

            try {
                Namespace namespace = Namespace.of(schemaName);

                // Verify the namespace exists by trying to load it
                Map<String, String> properties = ((RESTCatalog) Icebreaker.catalog).loadNamespaceMetadata(namespace);

                // Set the current schema
                Icebreaker.currentSchema = namespace;

                System.out.println("Using schema: " + namespace);
                if (!properties.isEmpty()) {
                    System.out.println("Schema properties: " + properties);
                }
            } catch (Exception e) {
                System.err.println("Failed to use schema '" + schemaName + "': " + e.getMessage());
                System.err.println("Use 'schemas' command to list available schemas.");
            }
        }
    }
}
