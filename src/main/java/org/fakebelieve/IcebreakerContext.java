package org.fakebelieve;

import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;

public class IcebreakerContext {
    private Catalog catalog;
    private String catalogName;
    private Namespace currentSchema;

    public Catalog getCatalog() {
        return catalog;
    }

    public void setCatalog(Catalog catalog) {
        this.catalog = catalog;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public void setCatalogName(String catalogName) {
        this.catalogName = catalogName;
    }

    public Namespace getCurrentSchema() {
        return currentSchema;
    }

    public void setCurrentSchema(Namespace currentSchema) {
        this.currentSchema = currentSchema;
    }

    public boolean activeCatalog() {
        if (catalog == null) {
            System.err.println("No catalog configured. Use 'catalog' command first.");
            return false;
        }
        return true;
    }

    public TableIdentifier tableIdentifier(String tableIdentifier) {
        TableIdentifier identifier = TableIdentifier.parse(tableIdentifier);
        if (!identifier.hasNamespace() && currentSchema != null) {
            identifier = TableIdentifier.of(currentSchema, identifier.name());
        }
        return identifier;
    }
}
