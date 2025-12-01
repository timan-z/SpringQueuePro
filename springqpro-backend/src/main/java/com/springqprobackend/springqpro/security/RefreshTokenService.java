package com.springqprobackend.springqpro.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/* RefreshTokenService.java
--------------------------------------------------------------------------------------------------
This file orchestrates Refresh Token rotation, storage, and validation. It also delegates
persistence to RedisTokenStore.java. This file ensures secure, OAuth2-style refresh
behavior with single-use refresh tokens, rotation on every login/refresh, and revocation
via user logout. (This feature was added as part of the JWT RBAC phase).

[FUTURE WORK]:
- Could integrate session metadata, device tracking, or audit logging. Who knows.
--------------------------------------------------------------------------------------------------
*/

@Service
public class RefreshTokenService {
    // Field(s):
    private final StringRedisTemplate redis;
    private final long ttlMs = 8L * 24 * 60 * 60 * 1000;    // refresh tokens last 8 days.
    // Constructor(s):
    public RefreshTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }
    // Method(s):
    private String key(String username) {
        return "refresh:" + username;
    }
    public void storeToken(String username, String refreshToken) {
        redis.opsForValue().set(
                key(username),
                refreshToken,
                ttlMs,
                TimeUnit.MILLISECONDS
        );
    }
    public String getStoredToken(String username) {
        return redis.opsForValue().get(key(username));
    }
    public void deleteToken(String username) {
        redis.delete(key(username));
    }
}
