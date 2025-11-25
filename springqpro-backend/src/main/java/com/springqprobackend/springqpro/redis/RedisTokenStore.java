package com.springqprobackend.springqpro.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisTokenStore {
    // Field(s):
    private final StringRedisTemplate redis;
    // Constructor(s):
    @Autowired
    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }
    // Method(s):
    // Save refresh token for email:
    public void storeRefreshToken(String refreshToken, String email, long ttlMs) {
        redis.opsForValue().set("refresh:" + refreshToken, email, ttlMs, TimeUnit.MILLISECONDS);
    }
    // Get email from refresh token:
    public String getEmailForToken(String refreshToken) {
        return redis.opsForValue().get("refresh:" + refreshToken);
    }
    // Delete refresh token:
    public void delete(String refreshToken) {
        redis.delete("refresh:" + refreshToken);
    }
}
