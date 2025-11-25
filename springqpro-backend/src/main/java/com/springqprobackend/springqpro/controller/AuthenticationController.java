package com.springqprobackend.springqpro.controller;

import com.springqprobackend.springqpro.domain.UserEntity;
import com.springqprobackend.springqpro.redis.RedisTokenStore;
import com.springqprobackend.springqpro.repository.UserRepository;
import com.springqprobackend.springqpro.security.*;
import org.apache.catalina.connector.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (userRepo.existsById(req.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered."));
        }
        String hash = encoder.encode(req.password());
        userRepo.save(new UserEntity(req.email(), hash));
        return ResponseEntity.ok(Map.of("status", "registered"));
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        UserEntity user = userRepo.findById(req.email()).orElse(null);
        if (user == null || !encoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid login credentials."));
        }
        String access = jwt.generateAccessToken(req.email());
        String refresh = jwt.generateRefreshToken(req.email());
        redis.storeRefreshToken(refresh, req.email(), refreshTtlMs);
        return ResponseEntity.ok(new AuthResponse(access, refresh));
    }
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest req) {
        String oldRefresh = req.refreshToken();
        // This first condition check here checks to see if a Token was even provided:
        if(oldRefresh == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No refresh token provided"));
        }
        String email = redis.getEmailForToken(oldRefresh);
        /* This second condition check here is a bit tricky. So here's what's going on:
        With the statement above this comment block, I'm checking to see if this token is currently stored in the Redis layer.
        If email returns null, that -- from an outsider perspective -- will mean either the Token expired (Redis TTL could have
        removed it automatically) or it didn't exist/was deleted/tampered with (and so Redis doesn't recognize it).
        The message that's sent relates to this ambiguity.
        */
        if(email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired refresh token"));
        }
        /* This third condition check here is still needed though because Redis isn't the "source of truth" (Redis only lets us
        know if the Token existed at some point or that the Token hasn't yet expired in Redis). Redis, however, will NOT guarantee
        whether the JWT's internal expiration claim is still valid, whether someone tampered with it externally (this is professional practice stuff),
        whether the access and refresh tokens were issued together, and whether the private signing key was rotated. */
        // SO ^ the condition below ACTUALLY checks to see if it's expired (because we know it's in Redis and *should* be a valid issued token:
        if (jwt.isExpired(oldRefresh)) {
            redis.delete(oldRefresh);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Refresh token expired"));
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
        return ResponseEntity.ok(new AuthResponse(newAccess, newRefresh));
    }
}
