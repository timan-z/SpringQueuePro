package com.springqprobackend.springqpro.integration;

// THIS IS INTEGRATION TEST FOR THE JWT ASPECT [2] -- for task ownership and that stuff.

import com.fasterxml.jackson.databind.JsonNode;
import com.redis.testcontainers.RedisContainer;
import com.springqprobackend.springqpro.security.dto.AuthResponse;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
@ActiveProfiles("test")
public class OwnershipGraphQLIntegrationTest {
    // Field(s):
    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private ObjectMapper objectMapper;

    /* 2025-11-26-NOTE:[BELOW] I AM RUSHING TO MVP COMPLETION, I HAVE GONE SEVERELY OVERTIME WITH THIS PROJECT AND I WANT TO
    WRAP IT UP AS SOON AS POSSIBLE READY FOR PRODUCTION AND DISPLAY FOR RECRUITERS AND READY FOR MY RESUME! THUS, MY
    INTEGRATION TESTS ARE A HOT MESS. I WILL COME BACK IN THE NEAR-FUTURE TO REFACTOR AND TIDY THEM. THE TWO CONTAINERS
    BELOW SHOULD 100% BE MODULARIZED SOMEWHERE -- BUT I AM ON A TIME CRUNCH AND I CAN'T BE ASKED (RIGHT NOW): */
    // DEBUG: BELOW! TEMPORARY - FIX PROPERLY LATER!
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18")
                    .withDatabaseName("springqpro")
                    .withUsername("springqpro")
                    .withPassword("springqpro")
                    .withReuse(true);
    @Container
    @ServiceConnection
    static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7.2"));
    // DEBUG: ABOVE! TEMPORARY - FIX PROPERLY LATER!
    /* 2025-11-26-NOTE: AS NOTED IN A COMMENT ABOVE THE CLASS, MY TEST STARTS THE FULL SPRING CONTEXT. BUT DON'T FORGET
    THAT I NEED TO SPIN UP THE REDIS AND POSTGRES CONTAINERS MYSELF!
    NOTE: ALSO, THE @Testcontainers annotation at the top is needed too for this stuff. */

    // For the tests:
    private static String aliceToken;
    private static String simonToken;
    private static String aliceTaskId;
    private static String simonTaskId;
    private static final String password = "who_cares";
    private static final String alice_email = "alice@gmail.com";
    private static final String simon_email = "simon@gmail.com";

    // HELPER METHODS (again, there's a lot of repeated boilerplate code here and in the other JWT test file -- modularize later, I'm rushing):
    // [1] - REGISTER:
    private void register(String email, String password) {
        webTestClient.post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "email", email,
                        "password", password
                ))
                .exchange()
                .expectStatus().isCreated();
    }
    // [2] - LOGIN:
    private AuthResponse login(String email, String password) {
        return webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "email", email,
                        "password", password
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();
    }
    // [3] - GRAPHQL QUERY:
    private String graphQLQuery(String token, String query) {
        return webTestClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
    // [4] - CREATE TASK (for specific User):
    private String createTaskForUser(String token, String payload, String type) throws Exception {
        String mutation = """
                mutation {
                  createTask(input: { payload: "%s", type: %s }) {
                    id
                    status
                  }
                }
                """.formatted(payload, type);
        String json = graphQLQuery(token, mutation);
        JsonNode root = objectMapper.readTree(json);
        JsonNode idNode = root.path("data").path("createTask").path("id");
        assertThat(idNode.isMissingNode()).isFalse();
        return idNode.asText();
    }

    // TESTS (they'll work in sequence with enforced ordering. When I refactor and clean things up, this ordering stays):
    // Test #1 - Setup: Register both users, login, and then store tokens:
    @Test
    @Order(1)
    void registerTestUsers_andLogin_thenStoreTokens() {
        register(alice_email, password); // Register Alice.
        register(simon_email, password); // Register Simon.
        // Login and then record tokens in the global variables.
        AuthResponse aliceAuth = login(alice_email, password);
        AuthResponse simonAuth = login(simon_email, password);
        aliceToken = aliceAuth.accessToken();
        simonToken = simonAuth.accessToken();
        // Make sure the login worked and returned tokens basically:
        assertThat(aliceToken).isNotBlank();
        assertThat(simonToken).isNotBlank();
    }

    // Test #2 - Alice creates a task and can see it.
    @Test
    @Order(2)
    void userOne_CreatesTask_andCanSeeIt() throws Exception {
        aliceTaskId = createTaskForUser(aliceToken, "Alice send email", "EMAIL");
        assertThat(aliceTaskId).isNotBlank();
        String json = graphQLQuery(aliceToken, """
                query {
                  tasks {
                    id
                    status
                  }
                }
                """);
        JsonNode root = objectMapper.readTree(json);
        JsonNode arr = root.path("data").path("tasks");
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThanOrEqualTo(1);
        boolean found = false;
        for (JsonNode t : arr) {
            if (aliceTaskId.equals(t.path("id").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    // Test #3 - Simon cannot see Alice's created task.
    @Test
    @Order(3)
    void userTwo_cannotSee_priorTask() throws Exception {
        String json = graphQLQuery(simonToken, """
                query {
                  tasks {
                    id
                    status
                  }
                }
                """);
        JsonNode root = objectMapper.readTree(json);
        JsonNode arr  = root.path("data").path("tasks");
        assertThat(arr.isArray()).isTrue();
        for (JsonNode t : arr) {
            assertThat(t.path("id").asText()).isNotEqualTo(aliceTaskId);
        }
    }

    // Test #4 - Simon cannot fetch Alice's task by ID.
    @Test
    @Order(4)
    void userTwo_cannotFetch_priorTaskById() throws Exception {
        String json = graphQLQuery(simonToken, """
                query {
                  task(id: "%s") {
                    id
                    status
                  }
                }
                """.formatted(aliceTaskId));
        JsonNode root = objectMapper.readTree(json);
        JsonNode node = root.path("data").path("task");
        assertThat(node.isNull()).isTrue();
    }

    // Test #5 - Simon cannot update Alice's task.
    @Test
    @Order(5)
    void userTwo_cannotUpdate_priorTask() throws Exception {
        String mutation = """
                mutation {
                  updateTask(input: {
                    id: "%s",
                    status: COMPLETED,
                    attempts: 999
                  }) {
                    id
                    status
                  }
                }
                """.formatted(aliceTaskId);
        String json = graphQLQuery(simonToken, mutation);
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataNode = root.path("data").path("updateTask");
        assertThat(dataNode.isNull()).isTrue();
        JsonNode errors = root.path("errors");
        assertThat(errors.isMissingNode() || errors.isArray()).isTrue();
    }
}
