package com.springqprobackend.springqpro.controller;

import com.springqprobackend.springqpro.domain.UserEntity;
import com.springqprobackend.springqpro.redis.RedisTokenStore;
import com.springqprobackend.springqpro.repository.UserRepository;
import com.springqprobackend.springqpro.security.*;
import org.apache.catalina.connector.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// 2025-11-24-NOTE(S):+DEBUG: This is the Controller file for registering and making an account.

@RestController
@RequestMapping("/auth")
public class AuthenticationController {
    // Field(s):
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final RedisTokenStore redis;
    private final long refreshTtlMs = 3L * 24 * 60 * 60 * 1000; // 3 days is fine.

    // Constructor(s):
    public AuthenticationController(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwt, RedisTokenStore redis) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.redis = redis;
    }

    // Method(s):
    // 2025-11-25-NOTE: Now will throw ResponseStatusException(HttpStatus.CONFLICT, ...); for already-existing email. GlobalExceptionHandler now formats the JSON response.
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest req) {
        if (userRepo.existsById(req.email())) {
            // 409 CONFLICT is what is usually used for "resource already exists."
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        String hash = encoder.encode(req.password());
        userRepo.save(new UserEntity(req.email(), hash));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "registered"));
    }
    // 2025-11-25-NOTE: Now will throw BadCredentialsException for invalid Login credentials. GlobalExceptionHandler now formats the JSON response.
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest req) {
        UserEntity user = userRepo.findById(req.email()).orElseThrow(() -> new BadCredentialsException("Invalid login credentials"));
        if(!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String access = jwt.generateAccessToken(user.getEmail());
        String refresh = jwt.generateRefreshToken(user.getEmail());
        redis.storeRefreshToken(refresh, user.getEmail(), refreshTtlMs);
        // Return pure DTO - GlobalExceptionHandler will shape errors:
        return new AuthResponse(access, refresh);
    }
    /* 2025-11-25-NOTE(S): refresh() now throws
    - BAD_REQUEST if token is missing
    - UNAUTHORIZED if redis can't find it and/or if JWT is expired.
    GlobalExceptionHandler now formats the JSON response.
    */
    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody RefreshRequest req) {
        String oldRefresh = req.refreshToken();
        // This first condition check here checks to see if a Token was even provided:
        if (oldRefresh == null || oldRefresh.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No refresh token provided");
        }
        // Look up in Redis (source of truth for issued/rotated tokens):
        String email = redis.getEmailForToken(oldRefresh);
        /* This second condition check here is a bit tricky. So here's what's going on:
        With the statement above this comment block, I'm checking to see if this token is currently stored in the Redis layer.
        If email returns null, that -- from an outsider perspective -- will mean either the Token expired (Redis TTL could have
        removed it automatically) or it didn't exist/was deleted/tampered with (and so Redis doesn't recognize it).
        The message that's sent relates to this ambiguity.
        */
        if (email == null) {
            // Could be expired from Redis TTL, never issued, or manually deleted
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        /* This third condition check here is still needed though because Redis isn't the "source of truth" (Redis only lets us
        know if the Token existed at some point or that the Token hasn't yet expired in Redis). Redis, however, will NOT guarantee
        whether the JWT's internal expiration claim is still valid, whether someone tampered with it externally (this is professional practice stuff),
        whether the access and refresh tokens were issued together, and whether the private signing key was rotated. */
        // SO ^ the condition below ACTUALLY checks to see if it's expired (because we know it's in Redis and *should* be a valid issued token:
        if (jwt.isExpired(oldRefresh)) {
            redis.delete(oldRefresh);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        /*
        Redis is the "source of truth" for refresh token persistence, but JWT is the source of truth for token validity.
        - Redis Check is basically: Has the refresh token been issued / rotated / invalidated?
        - JWT Check: Is the token cryptographically valid and still inside its lifetime?
        TL;DR = expired in Redis =/= expired in JWT!
        - Redis may outlive JWT expiration
        - Redis TTL may be longer
        - Redis time may drift
        - A token may be tampered
        - A token may be regenerated manually
        THE TWO CHECKS PROTECT AGAINST TWO DIFFERENT FAILURE CLASSES.
        */
        // Invalidate old refresh token (rotation best practice)
        redis.delete(oldRefresh);
        String newAccess = jwt.generateAccessToken(email);
        String newRefresh = jwt.generateRefreshToken(email);
        redis.storeRefreshToken(newRefresh, email, refreshTtlMs);
        return new AuthResponse(newAccess, newRefresh);
    }

    // 2025-11-25-NOTE: ADDING THIS ENDPOINT. HAVING THIS SHOULD BE COMMONSENSE BUT IT'S ALSO GOOD FOR REFRESH TOKEN REMOVAL.
    // No JWT validation here — logout is a server-side cleanup operation only. This is *apparently* exactly how Auth0, AWS Cognito, GitHub OAuth implement logout.
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody RefreshRequest req) {
        String refresh = req.refreshToken();
        if (refresh == null || refresh.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No refresh token provided");
        }
        // Just delete – even if nonexistent. Logout must be idempotent.
        redis.delete(refresh);
        return ResponseEntity.ok(Map.of("status", "logged out"));
    }
}
