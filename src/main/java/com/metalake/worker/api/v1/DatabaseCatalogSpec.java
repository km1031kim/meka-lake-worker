package com.metalake.worker.api.v1;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Validated, short-lived request data; never write passwords to SourceResource.configuration. */
public record DatabaseCatalogSpec(
        String provider,
        String host,
        int port,
        String database,
        String username,
        String password) {

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.-]*");
    private static final Pattern DB = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_-]*");

    public DatabaseCatalogSpec {

        if (provider == null) {
            throw new IllegalArgumentException("Database provider is required");
        }

        provider = provider.trim().toUpperCase(Locale.ROOT);

        if (!provider.equals("POSTGRESQL") && !provider.equals("MYSQL")) {
            throw new IllegalArgumentException("Supported DB providers: POSTGRESQL, MYSQL");
        }

        if (host == null || !HOST.matcher(host).matches()) {
            throw new IllegalArgumentException("Invalid DB host (this POC accepts DNS names and IPv4 addresses)");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid DB port");
        }

        if (provider.equals("POSTGRESQL") && (database == null || !DB.matcher(database).matches())) {
            throw new IllegalArgumentException("PostgreSQL database is required (letters, digits, _, -)");
        }

        if (database != null && !database.isBlank() && !DB.matcher(database).matches()) {
            throw new IllegalArgumentException("Invalid database name");
        }

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("DB username and password are required");
        }
    }

    public String gravitinoProvider() {
        return provider.equals("POSTGRESQL") ? "jdbc-postgresql" : "jdbc-mysql";
    }

    public String jdbcUrl() {
        return provider.equals("POSTGRESQL")
                ? "jdbc:postgresql://" + host + ":" + port + "/" + database
                : "jdbc:mysql://" + host + ":" + port;
    }

    public Map<String, String> catalogProperties() {
        Map<String, String> p = new LinkedHashMap<>();

        p.put("jdbc-url", jdbcUrl());
        p.put("jdbc-driver", provider.equals("POSTGRESQL") ? "org.postgresql.Driver" : "com.mysql.cj.jdbc.Driver");

        if (provider.equals("POSTGRESQL")) {
            p.put("jdbc-database", database);
        }

        p.put("jdbc-user", username);
        p.put("jdbc-password", password);

        return p;
    }

    public Map<String, Object> publicConfiguration() {
        Map<String, Object> p = new LinkedHashMap<>();

        p.put("provider", provider);
        p.put("host", host);
        p.put("port", port);

        if (database != null && !database.isBlank()) {
            p.put("database", database);
        }

        return p;
    }

    @Override
    public String toString() {
        return "DatabaseCatalogSpec[REDACTED]";
    }
}
