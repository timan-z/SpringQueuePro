package com.springqprobackend.springqpro.testcontainers;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/* BASE CLASS FOR ALL MY INTEGRATION TESTS THAT PROVIDES:
- Spring Boot test lifecycle.
- RANDOM_PORT server for GraphQL tests.
- Testcontainers lifecycle.
- PostgreSQL datasource auto-wiring from BasePostgresContainer.
*/
//public abstract class IntegrationTestBase extends BasePostgresContainer {
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.main.allow-bean-definition-overriding=true")
@Testcontainers
@ActiveProfiles("test")
public abstract class IntegrationTestBase {
    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected StringRedisTemplate redis;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE tasks RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18")
                    .withDatabaseName("springqpro")
                    .withUsername("springqpro")
                    .withPassword("springqpro")
                    .withReuse(true);

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withReuse(true);

    @DynamicPropertySource
    static void registerDynamicProps(DynamicPropertyRegistry registry) {
        // postgres
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // redis
        registry.add("spring.redis.host", REDIS::getHost);
        registry.add("spring.redis.port", () -> REDIS.getMappedPort(6379));
    }
}