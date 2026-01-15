package com.springqprobackend.springqpro.integration;

import com.redis.testcontainers.RedisContainer;
import com.springqprobackend.springqpro.security.dto.AuthResponse;
import com.springqprobackend.springqpro.testcontainers.IntegrationTestBase;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

// THIS IS INTEGRATION TEST FOR THE JWT ASPECT [1] -- for main stuff.

/* 2026-01-14-NOTE:+DEBUG:
Comments below are complete gibberish and I have no idea what i was going on about.
These "AuthJwtIntegrationTests" are more "Security contract tests" than anything specific (name could be changed to something else
like AuthJwtRedisIntegrationTests or just AuthIntegrationTests, maybe I should do that?).
- Basically test security around /graphql, /auth, and /api (boundary behavior, anonymous vs authenticated).

New tests that were added on this day as part of modernizing this test suite to be up-to-date:
- GraphQL access w/o Authorization header
- REST access w/o Authorization header
- Malformed JWT rejection (invalid JWT)
- Missing refresh token validation
- Expired JWT rejection even when it exists in Redis
- Refresh token rotation under concurrent reuse (race-condition safety)
- Refresh-status correctness
- User/token binding validation
^ these all go from ~test 6 onwards.
*/

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
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthJwtIntegrationTest extends AbstractAuthenticatedIntegrationTest {
    //@Autowired
    //private WebTestClient webTestClient;
    @Autowired
    private StringRedisTemplate redis;
    @Value("${jwt.secret}")
    private String jwtSecret;   // will be used for test that forges an expired JWT.

    // TESTS:
    @Test
    @Order(1)
    // NOTE: Related to the enforcing order thing.
    void fullLoginFlow_shouldAllowAccessToProtectedGraphQL() {
        AuthResponse auth = registerAndLogin(
                "random_email@gmail.com",
                "i_am_wanted_in_delaware"
        );

        String query = """
            query {
              tasks {
                id
                status
              }
            }
        """;

        graphQLWithToken(auth.accessToken(), query)
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
                .expectStatus().is4xxClientError();
    }

    @Test
    @Order(6)
    void graphqlAccess_shouldFail_withoutAuthorizationHeader() {
        String query = """
                query {
                  tasks {
                    id
                  }
                }
                """;
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    @Order(7)
    void restAccess_shouldFail_withoutAuthorizationHeader() {
        webTestClient.get()
                .uri("/api/tasks")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    @Order(8)
    void malformedJwt_shouldBeRejected() {
        String malformedToken = "this.aint.no.jwt.man";
        String query = """
                query {
                  tasks {
                    id
                  }
                }
                """;
        webTestClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + malformedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    @Order(9)
    void refresh_shouldFail_whenRefreshTokenMissing() {
        webTestClient.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())   // empty body
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists();
    }

    // This one's a bit more complex, but basically to show that Redis validity =/= JWT validity ("just because it's there, doesn't mean anything").
    @Test
    @Order(10)
    void refresh_shouldFail_whenJwtExpired_butRedisTokenExists() {
        String email = "expired_refresh@example.com";

        // Build expired refresh token manually
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        Instant now = Instant.now();
        Date issuedAt = Date.from(now.minusSeconds(7200));
        Date expiredAt = Date.from(now.minusSeconds(3600));

        String expiredRefresh = Jwts.builder()
                .subject(email)
                .claim("type", "refresh")
                .issuedAt(issuedAt)
                .expiration(expiredAt)
                .signWith(key)
                .compact();

        // Force-store expired token in Redis
        redis.opsForValue().set(expiredRefresh, email);

        webTestClient.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", expiredRefresh))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").exists();
    }

    /* This is actually complex - testing to make sure that a refresh token can only be used once, and
    doing so under a race condition where two threads attempt to refresh in proximity: */
    @Test
    @Order(11)
    void refreshToken_shouldOnlyBeUsableOnce_underConcurrency() {
        String email = "race@example.com";
        String password = "password";

        register(email, password);
        AuthResponse auth = login(email, password);
        String refresh = auth.refreshToken();

        Runnable refreshCall = () -> webTestClient.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", refresh))
                .exchange();

        Thread t1 = new Thread(refreshCall);
        Thread t2 = new Thread(refreshCall);

        t1.start();
        t2.start();

        Awaitility.await().untilAsserted(() ->
                assertThat(redis.opsForValue().get(refresh)).isNull()
        );
    }

    // Checking to see if the refresh token is still valid for the currently authenticated user:
    @Test
    @Order(12)
    void refreshStatus_shouldReflectRedisState() {
        String email = "status@example.com";
        String password = "password";

        register(email, password);
        AuthResponse auth = login(email, password);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/auth/refresh-status")
                        .queryParam("refreshToken", auth.refreshToken())
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(true);
    }

    // Basically testing for token substitution attacks here (making sure that you can't use another user's access token w/ your refresh):
    @Test
    @Order(13)
    void refreshStatus_shouldFail_whenAccessTokenDoesNotMatchRefreshTokenOwner() {
        register("user1@test.com", "pw");
        register("user2@test.com", "pw");

        AuthResponse u1 = login("user1@test.com", "pw");
        AuthResponse u2 = login("user2@test.com", "pw");

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/auth/refresh-status")
                        .queryParam("refreshToken", u1.refreshToken())
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + u2.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(false);
    }
}
