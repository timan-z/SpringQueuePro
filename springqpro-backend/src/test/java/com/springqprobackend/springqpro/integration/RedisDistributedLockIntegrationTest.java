package com.springqprobackend.springqpro.integration;

import com.redis.testcontainers.RedisContainer;
import com.springqprobackend.springqpro.config.RedisTestConfig;
import com.springqprobackend.springqpro.redis.RedisDistributedLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;

// NOTE: REDIS TEST

@DataRedisTest
@Testcontainers
@SpringJUnitConfig
@Import({ RedisTestConfig.class, RedisDistributedLock.class })
@TestPropertySource(properties = {
        "spring.profiles.active=test"
})
public class RedisDistributedLockIntegrationTest {
    @Container
    @ServiceConnection
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7.2"));
    @Autowired
    private RedisDistributedLock lock;
    @Autowired
    private StringRedisTemplate stringRedis;

    private static final String LOCK_KEY = "lock:test-item";

    @BeforeEach
    void cleanUp() {
        stringRedis.getRequiredConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
    }

    @Test
    void testAcquireLock_whenFree_shouldSucceed() {
        String token = lock.tryLock(LOCK_KEY, 2000);
        assertThat(token).isNotNull();
        // Verify key exists:
        String raw = stringRedis.opsForValue().get(LOCK_KEY);
        System.out.println("DEBUG: The value of raw => " + raw);
        assertThat(raw).isEqualTo(token);
    }
    @Test
    void testAcquireLock_whenAlreadyLocked_shouldFail() {
        String token1 = lock.tryLock(LOCK_KEY, 2000);
        assertThat(token1).isNotNull();
        // Verify lock:
        String token2 = lock.tryLock(LOCK_KEY, 2000);
        assertThat(token2).isNull(); // second lock must fail
    }

    @Test
    void testUnlock_withCorrectToken_shouldSucceed() {
        String token = lock.tryLock(LOCK_KEY, 2000);
        assertThat(token).isNotNull();
        boolean released = lock.unlock(LOCK_KEY, token);
        assertThat(released).isTrue();
        // Ensure lock is gone:
        String raw = stringRedis.opsForValue().get(LOCK_KEY);
        assertThat(raw).isNull();
    }

    @Test
    void testUnlock_withWrongToken_shouldFail() {
        String token = lock.tryLock(LOCK_KEY, 2000);
        assertThat(token).isNotNull();
        boolean released = lock.unlock(LOCK_KEY, "WRONG-TOKEN");
        assertThat(released).isFalse();
        // Lock must STILL exist:
        String raw = stringRedis.opsForValue().get(LOCK_KEY);
        assertThat(raw).isEqualTo(token);
    }

    @Test
    void testLockExpires_afterTTL() throws InterruptedException {
        String token = lock.tryLock(LOCK_KEY, 250); // 250ms TTL
        assertThat(token).isNotNull();
        // Key should exist immediately
        assertThat(stringRedis.opsForValue().get(LOCK_KEY)).isEqualTo(token);
        // Wait for TTL to expire
        Thread.sleep(350);  // NOTE:+TO-DO: Change this to something more professional later I'm rushing.
        // Key should be gone
        assertThat(stringRedis.opsForValue().get(LOCK_KEY)).isNull();
        // New lock should now succeed
        String token2 = lock.tryLock(LOCK_KEY, 1000);
        assertThat(token2).isNotNull();
    }
}
