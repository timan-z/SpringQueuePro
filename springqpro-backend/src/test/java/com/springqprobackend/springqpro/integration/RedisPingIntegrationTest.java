package com.springqprobackend.springqpro.integration;

import com.redis.testcontainers.RedisContainer;
import com.springqprobackend.springqpro.config.RedisTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// NOTE: REDIS TEST

//@ContextConfiguration(classes = { RedisPingIntegrationTest.Config.class })
//@Import(RedisTestConfig.class)
@DataRedisTest
@Testcontainers
@ExtendWith(SpringExtension.class)
class RedisPingIntegrationTest {
    @Container
    @ServiceConnection
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7.2"));

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testPing() {
        String result = stringRedisTemplate.execute((RedisCallback<String>) (conn) -> conn.ping());
        assert result.equalsIgnoreCase("PONG");
    }
}