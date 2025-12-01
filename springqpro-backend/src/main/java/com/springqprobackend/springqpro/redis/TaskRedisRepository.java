package com.springqprobackend.springqpro.redis;

import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import java.time.Duration;

/* TaskRedisRepository.java
--------------------------------------------------------------------------------------------------
This file is my Redis repository. Of course, it's basically a Redis-based caching layer for TaskEntity
objects used to accelerate the retrieval of recently processed tasks and offloading read-heavy queries.
It's not really part of the core processing pipeline and more of an overall system performance enhancer.
(Caching TaskEntity, the DataBase representation of our Tasks, rather than the domain in-memory Task is
appropriate too fitting DDD: "cache the authoritative persisted shape").

[FUTURE WORK]:
- Redis Streams for Task Event logs in preparation for CloudQueue, probably.
*/

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
