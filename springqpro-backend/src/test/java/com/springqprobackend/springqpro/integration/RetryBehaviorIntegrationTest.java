package com.springqprobackend.springqpro.integration;

import com.springqprobackend.springqpro.config.TaskHandlerProperties;
import com.springqprobackend.springqpro.config.TaskProcessingException;
import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.handlers.FailHandler;
import com.springqprobackend.springqpro.handlers.FailHandlerTests;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.repository.TaskRepository;
import com.springqprobackend.springqpro.service.QueueService;
import com.springqprobackend.springqpro.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

/* NOTE(S)-TO-SELF:
- @TestConfiguration is a specialized version of @Configuration designed specifically for defining beans
and customizations in a test environment. Meant to provide test-specific configurations without interfering
with the auto-detection of @SpringBootConfiguration (your app's primary config). [Google AI Overview, but sounds good]
*/
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class RetryBehaviorIntegrationTest {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(RetryBehaviorIntegrationTest.class);

    // NOTE: All the values provided below are just the arbitrary ones from docker_compose.yml:
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("springqpro")
            .withUsername("springqpro")
            .withPassword("springqpro");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    // Inject deterministic test handler for FAIL type in place of the real FailHandler class.
    /*@TestConfiguration
    static class DeterministicFailHandler {
        @Bean
        @Primary
        public FailHandler failHandler(Task t) {
            logger.info("DEBUG: ABOUT TO THROW NEW TASKPROCESSINGEXCEPTION!");
            throw new TaskProcessingException("Intentional fail for retry simulation [this time literally as part of the Integration Tests]");
        }
    }*/

    @Bean("FAIL")
    public TaskHandler deterministicFailHandler() {
        return task -> {
            logger.info("DEBUG: ABOUT TO THROW NEW TASKPROCESSINGEXCEPTION!");
            throw new TaskProcessingException("Intentional fail for retry simulation [this time literally as part of the Integration Tests]");
        };
    }




    @Test
    void failingTask_markedFailed_andRetryScheduled() {
        TaskEntity entity = taskService.createTask("RETRY TEST", TaskType.FAIL);
        String id = entity.getId();

        // wait for the attempt to be recorded (ProcessingService should claim and set attempts >=1)
        // Wait for the Task to be persisted and claimed by ProcessingService.
        Awaitility.await()
                .atMost(Duration.ofSeconds(8))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Optional<TaskEntity> maybe = taskRepository.findById(id);
                    assertThat(maybe).isPresent();
                    TaskEntity e = maybe.get();
                    // Make sure the processing attempt failed and attempts incremented.
                    assertThat(e.getAttempts()).isGreaterThanOrEqualTo(1);
                    assertThat(e.getStatus()).isEqualTo(TaskStatus.FAILED);
                });
        // Now ensure retry scheduling re-enqueued the task (attempts will increase after the scheduled retry occurs)
        Awaitility.await()
                .atMost(Duration.ofSeconds(12))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Optional<TaskEntity> maybe = taskRepository.findById(id);
                    assertThat(maybe).isPresent();
                    assertThat(maybe.get().getAttempts()).isGreaterThanOrEqualTo(2); // proof of re-enqueue, reclaim, etc.
                });
    }
}
