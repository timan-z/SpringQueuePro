package com.springqprobackend.springqpro.integration;

import com.redis.testcontainers.RedisContainer;
import com.springqprobackend.springqpro.security.AuthResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

// THIS IS INTEGRATION TEST FOR THE JWT ASPECT [1] -- for main stuff.

// 2025-11-26-NOTE: Remember, efficient setup of my Integration Tests are not high priority while I rush to project MVP completion. I can return to this later!
/* 2025-11-26-NOTE(S):
- @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) to load the FULL Spring context like it's a real server.
This makes sense testing JWT stuff because I want to verify that the security filter chain runs, tokens are parsed, method security
via @PreAuthorize is enforced, and /graphql and /auth/* behaves like in production. (Want SecurityConfig and so on present).
- @AutoConfigureWebTestClient will set up a WebTestClient to bound to the server. It's as the name implies, basically just
the dummy HTTP client for testing (use it to hit the HTTP endpoints, send headers, assert status codes, and so on).
- TestMethodOrder(...) is an interesting annotation I should make mental note of and probably use more in the future.
This basically allows you to control the order of tests which is useful when your tests build upon the same DB. (This can
be a nice alternative to what I was doing prior, which was flushing the DataBase between tests; this is probably better).
*/
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
@ActiveProfiles("test")
public class AuthJwtIntegrationTest {
    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private StringRedisTemplate redis;
    @Value("${jwt.secret}")
    private String jwtSecret;   // will be used for test that forges an expired JWT.

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

    // HELPER METHODS (pretty self-explanatory):
    // [1] - Register Attempt:
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

    // [2] - Login Attempt:
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

    // [3] - Sending a GraphQL query with authenticataion token:
    private WebTestClient.ResponseSpec graphQLWithToken(String token, String query) {
        return webTestClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange();
    }

    // TESTS:
    @Test
    @Order(1)
    // NOTE: Related to the enforcing order thing.
    void fullLoginFlow_shouldAllowAccessToProtectedGraphQL() {
        String email = "random_email@gmail.com";
        String password = "i_am_wanted_in_delaware";

        // 1. Register:
        register(email, password);
        // 2. Login and get tokens:
        AuthResponse auth = login(email, password);
        assertThat(auth).isNotNull();
        assertThat(auth.accessToken()).isNotBlank();
        assertThat(auth.refreshToken()).isNotBlank();

        String accessToken = auth.accessToken();

        // 3. Call protected GraphQL with access token
        String query = """
                query {
                  tasks {
                    id
                    status
                  }
                }
                """;

        graphQLWithToken(accessToken, query)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.tasks").exists();
    }

    @Test
    @Order(2)
        // This is why enforcing order is useful. I want this Test to execute after the one above.
    void registerAttempt_shouldFail_forExistingEmail() {
        String email = "random_email@gmail.com";
        String password = "I smell bad";
        // Second registration attempt (no real easy way to test for exception thrown apart from duplicating the register method logic w/ fail adjustment):
        webTestClient.post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "email", email,
                        "password", password
                ))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.status").doesNotExist();
    }

    @Test
    @Order(3)
    void refreshFlow_shouldRotateRefreshToken_andInvalidateOldRefresh() {

        //REDIS.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
        /*redis.execute((RedisConnection connection) -> {
            connection.serverCommands().flushAll();
            return null;
        });*/ // <-- FLUSH JUST FOR THIS TEST FOR NOW...

        String email = "jwt_refresh@gmail.com";
        String password = "I need a shower";
        register(email, password);
        AuthResponse auth = login(email, password);
        String oldAccess = auth.accessToken();
        String oldRefresh = auth.refreshToken();
        // Call /auth/refresh w/ old refresh token:
        AuthResponse refreshed = webTestClient.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", oldRefresh))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();
        // Making sure I get something new:
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();
        assertThat(refreshed.accessToken()).isNotEqualTo(oldAccess);
        assertThat(refreshed.refreshToken()).isNotEqualTo(oldRefresh);
        // Plugging in the old values won't work now thanks to rotation:
        webTestClient.post()
                .uri("/auth/refresh")
                .bodyValue(Map.of("refreshToken", oldRefresh))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Order(4)
    void logout_shouldInvalidate_refreshToken() {
        String email = "jwt_logout@gmail.com";
        String password = "I have a DUI in six different states";
        register(email, password);
        AuthResponse auth = login(email, password);
        String refresh = auth.refreshToken();
        // Logout:
        webTestClient.post()
                .uri("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", refresh))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("logged out");
        // Now an attempt to refresh with the same token (meant to fail):
        webTestClient.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", refresh))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Invalid or expired refresh token");
    }

    @Test
    @Order(5)
    void expiredJwtToken_shouldBeRejected_ForGraphQL() {
        // Make expired JWT manually using my secret key:
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        Instant now = Instant.now();
        Date pastIssuedAt = Date.from(now.minusSeconds(3600));  // 1 hour ago. (issued).
        Date pastExpiration = Date.from(now.minusSeconds(1800)); // expired 30 minutes ago.
        String expiredToken = Jwts.builder()
                .subject("expired-user@example.com")
                .issuedAt(pastIssuedAt)
                .expiration(pastExpiration)
                .signWith(key)
                .compact();
        String query = """
                query {
                  tasks {
                    id
                  }
                }
                """;

        // Because SecurityConfig requires authentication on /graphql, expired token will be caught by resolvers, send back 401, and so on.
        webTestClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange()
                .expectStatus().isForbidden();
    }
}
