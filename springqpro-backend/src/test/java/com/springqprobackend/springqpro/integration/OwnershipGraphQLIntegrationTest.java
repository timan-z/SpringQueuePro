package com.springqprobackend.springqpro.integration;

// THIS IS INTEGRATION TEST FOR THE JWT ASPECT [2] -- for task ownership and that stuff.

import com.fasterxml.jackson.databind.JsonNode;
import com.redis.testcontainers.RedisContainer;
import com.springqprobackend.springqpro.security.dto.AuthResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.queue.processing.enabled=false",
                "spring.task.scheduling.enabled=false"
        })
@ActiveProfiles("test")
public class OwnershipGraphQLIntegrationTest extends AbstractAuthenticatedIntegrationTest {
    // For the tests:
    private static String aliceToken;
    private static String simonToken;
    private static String aliceTaskId;
    //private static String simonTaskId;
    //private static final String password = "who_cares";
    private static String alice_email; // = "alice@gmail.com";
    private static String simon_email; // = "simon@gmail.com";

    // HELPER METHODS (specific to this test file):
    private String createTask(String token, String payload) {
        String mutation = """
            mutation {
              createTask(input: {
                payload: "%s"
                type: EMAIL
              }) {
                id
              }
            }
        """.formatted(payload);

        AtomicReference<String> taskId = new AtomicReference<>();

        graphQLWithToken(token, mutation)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.createTask.id")
                .value(id -> taskId.set((String) id));

        return taskId.get();
    }

    // TESTS:
    @Test
    void userCanSeeOnlyTheirOwnTasks() {
        AuthResponse alice = registerAndLogin("alice@test.com", "pw");
        AuthResponse simon = registerAndLogin("simon@test.com", "pw");

        String aliceTaskId = createTask(alice.accessToken(), "alice-task");

        // Alice sees her task
        graphQLWithToken(alice.accessToken(), """
            query {
              tasks {
                id
              }
            }
        """)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.tasks[*].id")
                .value(ids -> {
                    List<String> idList = (List<String>) ids;
                    assertThat(idList).contains(aliceTaskId); });

        // Simon does NOT see Alice's task
        graphQLWithToken(simon.accessToken(), """
            query {
              tasks {
                id
              }
            }
        """)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.tasks[*].id")
                .value(ids -> {
                        List<String> idList = (List<String>) ids;
                assertThat(idList).doesNotContain(aliceTaskId); });
    }

    @Test
    void userCannotFetchAnotherUsersTaskById() {
        AuthResponse alice = registerAndLogin("alice2@test.com", "pw");
        AuthResponse simon = registerAndLogin("simon2@test.com", "pw");

        String aliceTaskId = createTask(alice.accessToken(), "private-task");

        graphQLWithToken(simon.accessToken(), """
            query {
              task(id: "%s") {
                id
              }
            }
        """.formatted(aliceTaskId))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.task").isEmpty();
    }

    @Test
    void userCannotUpdateAnotherUsersTask() {
        AuthResponse alice = registerAndLogin("alice3@test.com", "pw");
        AuthResponse simon = registerAndLogin("simon3@test.com", "pw");

        String aliceTaskId = createTask(alice.accessToken(), "immutable-task");

        graphQLWithToken(simon.accessToken(), """
            mutation {
              updateTask(input: {
                id: "%s",
                status: COMPLETED,
                attempts: 999
              }) {
                id
              }
            }
        """.formatted(aliceTaskId))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.updateTask").isEmpty();
    }

    @Test
    void userCannotDeleteAnotherUsersTask() {
        AuthResponse alice = registerAndLogin("alice4@test.com", "pw");
        AuthResponse simon = registerAndLogin("simon4@test.com", "pw");

        String aliceTaskId = createTask(alice.accessToken(), "delete-protected");

        graphQLWithToken(simon.accessToken(), """
            mutation {
              deleteTask(id: "%s")
            }
        """.formatted(aliceTaskId))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.deleteTask").isEqualTo(false);
    }

    @Test
    void userCannotRetryAnotherUsersTask() {
        AuthResponse alice = registerAndLogin("alice5@test.com", "pw");
        AuthResponse simon = registerAndLogin("simon5@test.com", "pw");

        String aliceTaskId = createTask(alice.accessToken(), "retry-protected");

        graphQLWithToken(simon.accessToken(), """
            mutation {
              retryTask(id: "%s")
            }
        """.formatted(aliceTaskId))
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data").isEqualTo(null)
                    .jsonPath("$.errors").isArray()
                    .jsonPath("$.errors.length()").isEqualTo(1);
    }
}
