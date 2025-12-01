package com.springqprobackend.springqpro.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/* RedisTokenStore.java
--------------------------------------------------------------------------------------------------
This file stores and checks refresh tokens in Redis to enable server-side token revocation, token
rotation, and immediate logout with revocation of tokens. It's this file that ensures refresh
tokens can be invalidated immediately. (Originally refresh tokens were not stored server-side
but now Redis provides stateful control of them).

[FUTURE WORK]:
- Could integrate expiration events for auto-cleanup or Redisson buckets.
--------------------------------------------------------------------------------------------------
*/

@Component
public class RedisTokenStore {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(RedisTokenStore.class);
    private final StringRedisTemplate redis;
    // Constructor(s):
    @Autowired
    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }
    // Method(s):
    // Save refresh token for email:
    public void storeRefreshToken(String refreshToken, String email, long ttlMs) {
        logger.info("[RedisTokenStore] SET refreshToken (w/ email & ttl)");
        redis.opsForValue().set("refresh:" + refreshToken, email, ttlMs, TimeUnit.MILLISECONDS);
    }
    // Get email from refresh token:
    public String getEmailForToken(String refreshToken) {
        logger.info("[RedisTokenStore] GET refreshToken");
        return redis.opsForValue().get("refresh:" + refreshToken);
    }
    // Delete refresh token:
    public void delete(String refreshToken) {
        logger.info("[RedisTokenStore] DELETE refreshToken");
        redis.delete("refresh:" + refreshToken);
    }
}
