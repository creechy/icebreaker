package org.fakebelieve;

import static org.apache.iceberg.CatalogProperties.URI;
import static org.apache.iceberg.CatalogProperties.WAREHOUSE_LOCATION;
import static org.apache.iceberg.rest.auth.OAuth2Properties.CREDENTIAL;
import static org.apache.iceberg.rest.auth.OAuth2Properties.OAUTH2_SERVER_URI;
import static org.apache.iceberg.rest.auth.OAuth2Properties.SCOPE;
import static org.apache.iceberg.rest.auth.OAuth2Properties.TOKEN;
import static org.apache.iceberg.rest.auth.OAuth2Properties.TOKEN_EXCHANGE_ENABLED;

import java.util.HashMap;
import java.util.Map;
import org.apache.iceberg.CatalogUtil;
import picocli.CommandLine;

public class CatalogCommands {
    @CommandLine.Command(name = "catalog", description = "Configure Iceberg catalog")
    static class CatalogCommand implements Runnable {
        @CommandLine.Option(
                names = {"-n", "--name"},
                description = "Catalog name",
                required = true)
        String name;

        @CommandLine.Option(
                names = {"-c", "--class"},
                description = "Catalog class",
                defaultValue = "org.apache.iceberg.rest.RESTCatalog")
        String catalogClass;

        @CommandLine.Option(
                names = {"-u", "--uri"},
                description = "Catalog URI",
                required = true)
        String uri;

        @CommandLine.Option(
                names = {"-o", "--oauth2-server-uri"},
                description = "OAuth2 server URI",
                required = false)
        String oauth2ServerUri;

        @CommandLine.Option(
                names = {"-s", "--scope"},
                description = "OAuth2 scope",
                required = false)
        String scope;

        @CommandLine.Option(
                names = {"-d", "--no-exchange"},
                description = "Disable Token Exchange",
                defaultValue = "false")
        boolean tokenExchange;

        @CommandLine.Option(
                names = {"-V", "--vended-credentials"},
                description = "Use vended credentials",
                defaultValue = "false")
        boolean vendedCredentials;

        @CommandLine.Option(
                names = {"-w", "--warehouse"},
                description = "Warehouse location",
                required = false)
        String warehouse;

        @CommandLine.Option(
                names = {"-t", "--token"},
                description = "Authentication token",
                required = false)
        String token;

        @CommandLine.Option(
                names = {"-x", "--credential"},
                description = "Authentication credential",
                required = false)
        String credential;

        @CommandLine.Option(
                names = {"-h", "--header"},
                description = "Custom HTTP headers (format: header.name=value)")
        String[] headers;

        @Override
        public void run() {
            try {
                Map<String, String> props = new HashMap<>();
                props.put(URI, uri);
                if (warehouse != null) {
                    props.put(WAREHOUSE_LOCATION, warehouse);
                }
                if (token != null) {
                    props.put(TOKEN, token);
                }
                if (credential != null) {
                    props.put(CREDENTIAL, credential);
                }
                if (oauth2ServerUri != null) {
                    props.put(OAUTH2_SERVER_URI, oauth2ServerUri);
                }
                if (scope != null) {
                    props.put(SCOPE, scope);
                }
                if (tokenExchange) {
                    props.put(TOKEN_EXCHANGE_ENABLED, "true");
                }
                if (vendedCredentials) {
                    props.put("header.X-Iceberg-Access-Delegation", "vended-credentials");
                }

                if (headers != null) {
                    for (String header : headers) {
                        if (header.contains("=")) {
                            String[] parts = header.split("=", 2);
                            String key = parts[0].trim();
                            String value = parts[1].trim();

                            // Ensure header properties have the correct prefix
                            if (!key.startsWith("header.")) {
                                key = "header." + key;
                            }
                            props.put(key, value);
                        }
                    }
                }

                Icebreaker.catalog = CatalogUtil.loadCatalog(catalogClass, name, props, null);
                Icebreaker.catalogName = name;

                System.out.println("Catalog '" + name + "' configured successfully");
                System.out.println("Class: " + catalogClass);
                System.out.println("URI: " + uri);
                if (oauth2ServerUri != null) {
                    System.out.println("OAuth2 server URI: " + oauth2ServerUri);
                }
                if (scope != null) {
                    System.out.println("OAuth2 scope: " + scope);
                }
                System.out.println("Warehouse: " + ((warehouse != null) ? warehouse : "(none)"));
            } catch (Exception e) {
                System.err.println("Failed to configure catalog: " + e.getMessage());
            }
        }
    }
}
