package com.springqprobackend.springqpro.testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/* 2025-11-23-NOTE(S):+DEBUG:
SO, WHAT I WANT TO DO IS REFACTOR MY TEST SETUP SO THAT THE INTEGRATION TESTS ARE LESS "HEAVY".
I HAVE A PROBLEM RIGHT NOW WHERE MY 4 CORE NON-REDIS RELATED INTEGRATION TESTS ARE LOADING THE WHOLE
SPRING CONTEXT TO RUN EACH TEST WHICH IS SLOW AND INEFFICIENT. BUT I'VE REFACTORED PROCESSINGSERVICE.JAVA
AND TASKSERVICE.JAVA TO INTEGRATE REDIS AS PART OF REFACTORING. SO THE BEST FIX I CAN HAVE FOR NOW
IS TO JUST SPIN UP A REDIS CONTAINER INSIDE OF THIS FILE -- BUT IT'S ONLY TEMPORARY, AND I WILL COME BACK
TO FIX THIS AND MAKE IT MORE PROFESSIONALLY TIDY AFTER I GET MY CORE PROJECT DONE AND RESUME READY. (ON TIME CRUNCH).
*/
// CENTRAL PostgreSQL Testcontainer USED FOR ALL MY INTEGRATION TESTS:
public abstract class BasePostgresContainer {
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18")
                    .withDatabaseName("springqpro")
                    .withUsername("springqpro")
                    .withPassword("springqpro")
                    .withReuse(true);
    // 2025-11-23-DEBUG: TEMP BELOW.
    @Container
    @ServiceConnection
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7.2"));
    // 2025-11-23-DEBUG: TEMP ABOVE.

    static {
        POSTGRES.start();   // This is meant to boot once for the entire Integration Test Suite.
        REDIS.start();  // 2025-11-23-DEBUG: TEMP.
    }
}
