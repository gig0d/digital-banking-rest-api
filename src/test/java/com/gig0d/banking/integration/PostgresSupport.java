package com.gig0d.banking.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.UUID;

public abstract class PostgresSupport {
    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;
    private static final String SCHEMA = "banking_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SECRET;
    private static PostgreSQLContainer container;
    static {
        String external = System.getenv("TEST_DB_URL");
        if (external == null || external.isBlank()) {
            container = new PostgreSQLContainer("postgres:17.7-alpine")
                    .withDatabaseName("banking_test").withUsername("banking_test").withPassword(UUID.randomUUID().toString());
            container.start(); // Deliberately fail, rather than silently skip, when Docker is unavailable.
            URL = container.getJdbcUrl(); USER = container.getUsername(); PASSWORD = container.getPassword();
        } else {
            URL = external;
            USER = System.getenv().getOrDefault("TEST_DB_USER", "banking_test");
            PASSWORD = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");
        }
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        SECRET = Base64.getEncoder().encodeToString(bytes);
    }
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> USER);
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("banking.jwt.secret", () -> SECRET);
        properties.add("banking.demo.enabled", () -> "true");
        properties.add("banking.demo.admin-email", () -> "");
        properties.add("springdoc.api-docs.enabled", () -> "true");
        properties.add("springdoc.swagger-ui.enabled", () -> "true");
    }
}
