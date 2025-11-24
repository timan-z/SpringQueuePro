package com.springqprobackend.springqpro.testcontainers;

import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

/* BASE CLASS FOR ALL MY INTEGRATION TESTS THAT PROVIDES:
- Spring Boot test lifecycle.
- RANDOM_PORT server for GraphQL tests.
- Testcontainers lifecycle.
- PostgreSQL datasource auto-wiring from BasePostgresContainer.
*/
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.main.allow-bean-definition-overriding=true")
@Testcontainers
public abstract class IntegrationTestBase extends BasePostgresContainer {
}