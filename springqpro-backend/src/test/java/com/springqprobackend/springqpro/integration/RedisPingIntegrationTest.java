package com.springqprobackend.springqpro.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.springqprobackend.springqpro.config.RedisConfig;
import org.testcontainers.utility.DockerImageName;

@DataRedisTest
@Testcontainers
@ContextConfiguration(classes = { RedisPingIntegrationTest.Config.class })
class RedisPingIntegrationTest {

    @TestConfiguration
    @Import(com.springqprobackend.springqpro.config.RedisConfig.class)
    static class Config {
        static GenericContainer<?> REDIS =
                new GenericContainer<>(DockerImageName.parse("redis:7.2"))
                        .withExposedPorts(6379);
        static {
            REDIS.start();
            System.setProperty("spring.data.redis.host", REDIS.getHost());
            System.setProperty("spring.data.redis.port", REDIS.getMappedPort(6379).toString());
        }
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testPing() {
        System.out.println("What's poppin!");
    }
}