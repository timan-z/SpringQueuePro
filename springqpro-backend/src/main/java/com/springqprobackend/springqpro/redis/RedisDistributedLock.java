package com.springqprobackend.springqpro.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/* RedisDistributedLock.java
--------------------------------------------------------------------------------------------------
This file implements a lightweight Redis-based distributed lock using the Redis command:
"SET key value NX PX ttl". So, this is used in ProcessingService.java, particularly its claimAndProcess()
method that deals with enqueued Tasks. That file originally relied on, still present, Atomic state transition
methods as a way to "lock" down threads using ProcessService to process Tasks and prevent double-processing.
With the integration of Redis, this more secure Redis Distributed Locks are like an extra layer of
security making sure that only one worker thread can process a task at a time.
- Also has method with a Lua script inside that checks token.

Supposedly, the in-memory locking would fail in multi-instance environments and so Redis optimistic locks
compensate for that shortcoming. (I don't know how to test this).

[FUTURE WORK]: For CloudQueue, these features amy be added:
- Redisson locks.
- DynamoDB TTL-based locks.
- SQS visibility timeouts.
*/

/* 2025-11-21-REDIS-PHASE-NOTE:
- Safe lock using SET key value NX PX ms and a Lua script unlock that checks token (prevents deleting another holder’s lock).
- How to use: String token = lock.tryLock("task:lock:"+id, 10000); if(token!=null){ try { ... } finally { lock.unlock(...); } }
SO BASICALLY, safe lock happens using SET NX PX, and we use a Lua script to unlock it. That's how this is going to work.
*/
@Component
public class RedisDistributedLock {
    // Field(s):
    //private final RedisTemplate<String, Object> redis;
    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLock.class);
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
        logger.info("[RedisLock] TRY key={} token={} success={}", key, token, ok);
        if(Boolean.TRUE.equals(ok)) return token;
        return null;
    }
    public boolean unlock(String key, String token) {
        Long res = redis.execute(releaseScript, Collections.singletonList(key), token);
        logger.info("[RedisLock] UNLOCK key={} token={} -> {}", key, token, res);
        return res != null && res > 0;
    }
}
