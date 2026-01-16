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
import java.util.UUID;

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
@ActiveProfiles("test")
public class AuthJwtIntegrationTest extends AbstractAuthenticatedIntegrationTest {
    @Autowired
    private StringRedisTemplate redis;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // Registration & Login:
    @Test
    void userCanRegisterAndLoginSuccessfully() {
        String email = "login-" + UUID.randomUUID() + "@test.com";
        String password = "pw";

        AuthResponse auth = registerAndLogin(email, password);

        assertThat(auth).isNotNull();
        assertThat(auth.accessToken()).isNotBlank();
        assertThat(auth.refreshToken()).isNotBlank();
    }

    @Test
    void duplicateRegistrationIsRejected() {
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        String password = "pw";

        register(email, password);

        webTestClient.post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "email", email,
                        "password", password
                ))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    // Protected Access:
    @Test
    void authenticatedUserCanAccessProtectedGraphQL() {
        AuthResponse auth = registerAndLogin(
                "graphql-" + UUID.randomUUID() + "@test.com",
                "pw"
        );

        graphQLWithToken(auth.accessToken(), """
            query {
              tasks {
                id
              }
            }
        """)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.tasks").exists();
    }

    @Test
    void graphqlAccessFailsWithoutAuthorizationHeader() {
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "query", "query { tasks { id } }"
                ))
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void restAccessFailsWithoutAuthorizationHeader() {
        webTestClient.get()
                .uri("/api/tasks")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    // JWT Validation:
    @Test
    void malformedJwtIsRejected() {
        webTestClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "query", "query { tasks { id } }"
                ))
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void expiredJwtIsRejected() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        Instant now = Instant.now();
        String expiredToken = Jwts.builder()
                .subject("expired@test.com")
                .issuedAt(Date.from(now.minusSeconds(3600)))
                .expiration(Date.from(now.minusSeconds(1800)))
                .signWith(key)
                .compact();

        webTestClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "query", "query { tasks { id } }"
                ))
                .exchange()
                .expectStatus().is4xxClientError();
    }

    // Refresh Token Flow:
    @Test
    void refreshRotatesTokensAndInvalidatesOldRefresh() {
        String email = "refresh-" + UUID.randomUUID() + "@test.com";
        AuthResponse auth = registerAndLogin(email, "pw");

        String oldAccess = auth.accessToken();
        String oldRefresh = auth.refreshToken();

        AuthResponse refreshed = webTestClient.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", oldRefresh))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(refreshed.accessToken()).isNotEqualTo(oldAccess);
        assertThat(refreshed.refreshToken()).isNotEqualTo(oldRefresh);

        // old refresh token is invalid
        webTestClient.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", oldRefresh))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshFailsWhenTokenMissing() {
        webTestClient.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void refreshFailsWhenJwtExpiredEvenIfRedisEntryExists() {
        String email = "expired-refresh-" + UUID.randomUUID() + "@test.com";

        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        Instant now = Instant.now();
        String expiredRefresh = Jwts.builder()
                .subject(email)
                .claim("type", "refresh")
                .issuedAt(Date.from(now.minusSeconds(7200)))
                .expiration(Date.from(now.minusSeconds(3600)))
                .signWith(key)
                .compact();

        redis.opsForValue().set(expiredRefresh, email);

        webTestClient.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", expiredRefresh))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshTokenIsSingleUseUnderConcurrency() {
        String email = "race-" + UUID.randomUUID() + "@test.com";
        AuthResponse auth = registerAndLogin(email, "pw");

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

    // Refresh Status:
    @Test
    void refreshStatusReflectsRedisState() {
        String email = "status-" + UUID.randomUUID() + "@test.com";
        AuthResponse auth = registerAndLogin(email, "pw");

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

    @Test
    void refreshStatusFailsWhenAccessTokenDoesNotMatchOwner() {
        AuthResponse u1 = registerAndLogin(
                "u1-" + UUID.randomUUID() + "@test.com", "pw");
        AuthResponse u2 = registerAndLogin(
                "u2-" + UUID.randomUUID() + "@test.com", "pw");

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
