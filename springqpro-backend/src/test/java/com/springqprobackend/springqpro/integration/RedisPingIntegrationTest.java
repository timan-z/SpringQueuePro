/*package com.springqprobackend.springqpro.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.springqprobackend.springqpro.config.RedisConfig;

@SpringBootTest(classes = { RedisConfig.class })
@ActiveProfiles("test")
@Testcontainers
public class RedisPingIntegrationTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2")
                    .withExposedPorts(6379);

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @BeforeAll
    static void setup() {
        System.setProperty("spring.redis.host", redis.getHost());
        System.setProperty("spring.redis.port",
                redis.getMappedPort(6379).toString());
    }

    @Test
    void redisPingPong() {
        String key = "ping";
        String value = "pong";

        redisTemplate.opsForValue().set(key, value);

        String fetched = (String) redisTemplate.opsForValue().get(key);

        assert fetched != null;
        assert fetched.equals(value);
    }
}
*/