package com.springqprobackend.springqpro.integration;

import com.redis.testcontainers.RedisContainer;
import com.springqprobackend.springqpro.config.RedisTestConfig;
import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.redis.TaskRedisRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

/* 2025-11-23-NOTE(S):
- This Integration test verifies that writes to and reads from the Redis layer work.
- Tests JSON serialization via Jackson.
- That TaskRedisRepository.java uses the correct key format.
- Maybe also tests TTL? <-- TO-DO: Maybe come back and do this?
*/
/*@DataRedisTest
@Testcontainers
@SpringJUnitConfig
@TestPropertySource(properties = {
        "spring.profiles.active=test" // disables main RedisConfig
})
@Import({ RedisTestConfig.class, TaskRedisRepository.class })*/
// DEBUG: ^ "@DataRedisTest aggressively filters components. It will NOT load a repository unless you import it before slicing."
// DEBUG: so basically I got to do the stuff below now.
/*@SpringJUnitConfig(classes = {
        RedisTestConfig.class,
        TaskRedisRepository.class
})
@DataRedisTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.profiles.active=test"
})*/

@DataRedisTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.profiles.active=test"
})
@Import({ RedisTestConfig.class, TaskRedisRepository.class })
class TaskCacheIntegrationTest {
    @Container
    @ServiceConnection
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7.2"));
    @Autowired
    private TaskRedisRepository cache;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void saveTask_toRedis_canRetrieveFetchTask() {
        // Create Task:
        TaskEntity task = new TaskEntity(
                "Task-ArbitraryTaskId",
                "Send an email",
                TaskType.EMAIL,
                TaskStatus.QUEUED,
                0,
                3,
                Instant.now()
        );
        // Save it to the Redis layer:
        cache.put(task);
        // Verify the Redis key exists:
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        //String rawJson = ops.get("task:Task-ArbitraryTaskId");
        String rawJson = stringRedisTemplate.opsForValue()
                .get("task:Task-ArbitraryTaskId");
        System.out.println("DEBUG RAW = " + rawJson);
        assertThat(rawJson).isNotNull();

        // Fetch via repository
        TaskEntity fetched = cache.get("Task-ArbitraryTaskId");
        assertThat(fetched).isNotNull();
        assertThat(fetched.getPayload()).isEqualTo("Send an email");
        assertThat(fetched.getType()).isEqualTo(TaskType.EMAIL);
    }
}
