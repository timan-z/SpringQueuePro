package com.springqprobackend.springqpro.redis;

import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.time.Duration;

// A thin cache abstraction. Keep logic here so you can change eviction/format later.
// Note: We cache TaskEntity (DB representation) rather than domain Task. This fits DDD: cache the authoritative persisted shape.

/* 2025-11-21-REDIS-PHASE-NOTE:
- This file is a thin cache abstraction.
- We'll be caching TaskEntity (DB representation, not domain Task). This is good DDD (Domain-Driven Design): Cache the authoritative persisted shape.
*/
// NOTE:+DEBUG: During refactoring, I should clean things up and probably add this file to package repository instead of this "redis" package. (Avoided for now to keep integration simple).
@Repository
public class TaskRedisRepository {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(TaskRedisRepository.class);
    private static final String TASK_KEY_PREFIX = "task:";
    private final RedisTemplate<String, Object> redis;
    private final Duration ttl;
    // Constructor(s):
    public TaskRedisRepository(RedisTemplate<String, Object> redis, @Value("${cache.task.ttl-seconds:600}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }
    // Method(s):
    private String key(String id) {
        return TASK_KEY_PREFIX + id;
    }

    public void put(TaskEntity entity) {
        if(entity == null || entity.getId() == null) return;
        logger.info("[TaskRedisRepository](aka RedisCache) PUT {}", entity.getId());
        redis.opsForValue().set(key(entity.getId()), entity, ttl);
    }

    public TaskEntity get(String id) {
        Object o = redis.opsForValue().get(key(id));
        if(o == null || !(o instanceof TaskEntity)) return null;    // DEBUG: Maybe add logs too or something.
        if(o instanceof TaskEntity) return (TaskEntity) o;
        logger.info("[TaskRedisRepository](aka RedisCache) GET {}", id);
        return null;
    }

    public void delete(String id) {
        logger.info("[TaskRedisRepository](aka RedisCache) DELETE {}", id);
        redis.delete(key(id));
    }
    // DEBUG:+NOTE:+TO-DO: I can add other methods like exists(), setIfAbsent() and so on...
}
