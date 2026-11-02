package com.propertyseacher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgreSqlTestcontainersTest {

    @Test
    @DisplayName("Verify Testcontainers PostgreSQL 16 image specification and connectivity")
    void shouldConfigurePostgreSql16Container() throws Exception {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
        }

        assumeTrue(dockerAvailable, "Docker daemon not active in local host environment; skipping container launch.");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("test_property_db")
                .withUsername("test_user")
                .withPassword("test_pass")) {

            assertEquals("postgres:16-alpine", postgres.getDockerImageName());
            postgres.start();

            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version();")) {

                assertTrue(rs.next());
                String version = rs.getString(1);
                assertTrue(version.toLowerCase().contains("postgresql 16") || version.toLowerCase().contains("postgresql"),
                        "Expected PostgreSQL version output but got: " + version);
            }
        }
    }
}
