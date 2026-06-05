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
        private final IcebreakerContext context;

        public ListSchemasCommand(IcebreakerContext context) {
            this.context = context;
        }

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            try {
                // List namespaces under the root namespace (empty namespace)
                List<Namespace> schemas = ((RESTCatalog) context.getCatalog()).listNamespaces(Namespace.empty());

                if (schemas.isEmpty()) {
                    System.out.println("No schemas found in catalog '" + context.getCatalogName() + "'");
                } else {
                    System.out.println("Schemas in catalog '" + context.getCatalogName() + "':");
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
        private final IcebreakerContext context;

        public UseSchemaCommand(IcebreakerContext context) {
            this.context = context;
        }

        @CommandLine.Parameters(description = "Schema name to use")
        String schemaName;

        @Override
        public void run() {
            if (!context.activeCatalog()) {
                return;
            }

            try {
                Namespace namespace = Namespace.of(schemaName);

                // Verify the namespace exists by trying to load it
                Map<String, String> properties = ((RESTCatalog) context.getCatalog()).loadNamespaceMetadata(namespace);

                // Set the current schema
                context.setCurrentSchema(namespace);

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
