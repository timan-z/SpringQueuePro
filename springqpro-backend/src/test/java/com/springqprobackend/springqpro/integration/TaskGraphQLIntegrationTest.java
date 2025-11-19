package com.springqprobackend.springqpro.integration;

import com.springqprobackend.springqpro.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/* 2025-11-17-NOTE(S)-TO-SELF:
- GraphQlTester is Spring's testing utility for GraphQL endpoints.
It's the GraphQL equivalent of [MockMvc for REST], [WebTestClient for reactive HTTP], and [TestRestTemplate for REST integration tests].
GraphQlTester lets you send queries/mutations, inspect fields in the GraphQL response, deserialize response fields into
Java objects (like String, Integer, custom stuff) and assert values using a fluent, chainable API.
*/
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class TaskGraphQLIntegrationTest {
    private GraphQlTester graphQlTester;

    @LocalServerPort
    private int port;

    @Autowired
    private TaskRepository taskRepository;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("springqpro")
            .withUsername("springqpro")
            .withPassword("springqpro");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @BeforeEach
    void init() {
        taskRepository.deleteAll();
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/graphql")
                .exchangeStrategies(
                        ExchangeStrategies.builder()
                                .codecs(c -> c.defaultCodecs().maxInMemorySize(5_000_000))
                                .build()
                )
                .build();
        this.graphQlTester = HttpGraphQlTester.create(client);
    }

    // Test #1 - Create Task, Query the Task, then verify its Status, Type, and Payload:
    @Test
    void taskCreation_succeeds_isRetrieved() {
        String mutation = """
                mutation {
                    createTask(input:{
                        payload:"send-an-email"
                        type:EMAIL
                    }) {
                        id
                        payload
                        type
                        status
                        attempts
                        maxRetries
                    }
                }
                """;
        /*2025 - 11 - 17 - NOTE(S) - TO - SELF:+DEBUG:
        -graphQlTester.document(mutation) prepares a GraphQL mutation with String mutation, which is just a String
        containing
        a proper GraphQL mutation obv.So graphQlTester runs it by running my actual Spring Boot app(which runs my real
                GraphQL controller, my real service layer, and uses the real database(Testcontainers in this
        case)).
        - .execute() clearly runs it and you can inspect the fields.*/
        var created = graphQlTester.document(mutation)
        .execute()
        .path("createTask.payload").entity(String.class).isEqualTo("send-an-email")
        .path("createTask.type").entity(String.class).isEqualTo("EMAIL")
        .path("createTask.status").entity(String.class).isEqualTo("QUEUED");
        // Extract the id from the same GraphQL mutation:
        String id = graphQlTester.document(mutation)
                .execute()
                .path("createTask.id").entity(String.class).get();
        // Search for that Task w/ a GraphQL query:
        String query = """
                query {
                    task(id: "%s") {
                        id
                        payload
                        type
                        status
                    }
                }
                """.formatted(id);
        graphQlTester.document(query)
                .execute()
                .path("task.payload").entity(String.class).isEqualTo("send-an-email")
                .path("task.type").entity(String.class).isEqualTo("EMAIL");
    }

}
