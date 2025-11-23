package com.springqprobackend.springqpro.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/* 2025-11-21-REDIS-PHASE-NOTE:
- Safe lock using SET key value NX PX ms and a Lua script unlock that checks token (prevents deleting another holder’s lock).
- How to use: String token = lock.tryLock("task:lock:"+id, 10000); if(token!=null){ try { ... } finally { lock.unlock(...); } }
SO BASICALLY, safe lock happens using SET NX PX, and we use a Lua script to unlock it. That's how this is going to work.
*/
@Component
public class RedisDistributedLock {
    // Field(s):
    //private final RedisTemplate<String, Object> redis;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> releaseScript;
    // Constructor(s):
    //public RedisDistributedLock(RedisTemplate<String, Object> redis) {
    public RedisDistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
        // [FOR UNLOCKING] -> Lua script: if redis.get(KEY)==ARGV[1] then del(KEY) return 1 else return 0 end.
        String lua =
                "if redis.call('get', KEYS[1]) == ARGV[1] " +
                        "then return redis.call('del', KEYS[1]) " +
                        "else return 0 end";
        releaseScript = new DefaultRedisScript<>();
        releaseScript.setScriptText(lua);
        releaseScript.setResultType(Long.class);
    }
    // Methods:
    public String tryLock(String key, long ttlMs) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofMillis(ttlMs));
        if(Boolean.TRUE.equals(ok)) return token;
        return null;
    }
    public boolean unlock(String key, String token) {
        Long res = redis.execute(releaseScript, Collections.singletonList(key), token);
        return res != null && res > 0;
    }
}
