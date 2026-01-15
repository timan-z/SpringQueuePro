package com.springqprobackend.springqpro.integration;

import com.springqprobackend.springqpro.security.dto.AuthResponse;
import com.springqprobackend.springqpro.testcontainers.IntegrationTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

@AutoConfigureWebTestClient
public abstract class AbstractAuthenticatedIntegrationTest extends IntegrationTestBase {
    @Autowired
    protected WebTestClient webTestClient;

    // auth helper methods
    protected void register(String email, String password) {
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

    protected AuthResponse login(String email, String password) {
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

    protected WebTestClient.ResponseSpec graphQLWithToken(String token, String query) {
        return webTestClient.post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query))
                .exchange();
    }

    protected AuthResponse registerAndLogin(String email, String password) {
        register(email, password);
        return login(email, password);
    }
}
