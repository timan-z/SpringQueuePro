package com.springqprobackend.springqpro.testcontainers;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

// CENTRAL PostgreSQL Testcontainer USED FOR ALL MY INTEGRATION TESTS:
public abstract class BasePostgresContainer {
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18")
                    .withDatabaseName("springqpro")
                    .withUsername("springqpro")
                    .withPassword("springqpro")
                    .withReuse(true);

    static {
        POSTGRES.start();   // This is meant to boot once for the entire Integration Test Suite.
    }
}
