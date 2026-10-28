package com.metalake.worker.api.v1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseCatalogSpecTest {
    @Test void postgresCatalogAndPublicConfig() {
        var spec = new DatabaseCatalogSpec("postgresql", "db.example", 5432, "appdb", "readonly", "SECRET");
        assertEquals("jdbc-postgresql", spec.gravitinoProvider());
        assertEquals("jdbc:postgresql://db.example:5432/appdb", spec.jdbcUrl());
        assertEquals("appdb", spec.catalogProperties().get("jdbc-database"));
        assertFalse(spec.publicConfiguration().containsKey("jdbc-password"));
        assertFalse(spec.publicConfiguration().containsKey("password"));
        assertFalse(spec.toString().contains("SECRET"));
    }

    @Test void mysqlCatalogAndPublicConfig() {
        var spec = new DatabaseCatalogSpec("mysql", "db.internal", 3306, null, "readonly", "SECRET");
        assertEquals("jdbc-mysql", spec.gravitinoProvider());
        assertEquals("jdbc:mysql://db.internal:3306", spec.jdbcUrl());
        assertFalse(spec.catalogProperties().containsKey("jdbc-database"));
    }

    @Test void rejectJdbcUrlHostInjection() {
        assertThrows(IllegalArgumentException.class, () ->
                new DatabaseCatalogSpec("postgresql", "host/name?param=x", 5432, "appdb", "u", "p"));
    }
}
