package com.springqprobackend.springqpro.testcontainers;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;

public abstract class BaseRedisContainer {
    @ServiceConnection
    protected static final GenericContainer<?> REDIS
            = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);
    static {
        REDIS.start();
    }
}
