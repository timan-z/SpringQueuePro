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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/* NOTE(S)-TO-SELF:
- @TestConfiguration is a specialized version of @Configuration designed specifically for defining beans
and customizations in a test environment. Meant to provide test-specific configurations without interfering
with the auto-detection of @SpringBootConfiguration (your app's primary config). [Google AI Overview, but sounds good]
- @SpyBean is a Spring Boot testing annotation that integrates Mockito's spying capabilities within a Spring ApplicationContext.
It is specifically designed for integration tests where you need to interact with Spring-managed beans.
-- Unlike @Spy (a pure Mockito annotation for non-Spring objects), @SpyBean targets beans managed by the Spring container.
-- @SpyBean lets you crete a spy that wraps an existing Spring bean. This means you can selectively mock certain methods of the
bean for testing purposes while allowing other methods to execute their real implementations.
-- @SpyBean is primarily used in Spring Boot Integration Tests [like this one] to isolate parts of my application and
test specific interactions between beans.
-- If a Bean of the required type already exists in the ApplicationContext, @SpyBean will wrap it up in a spy.
And if no such bean exists, it will create a new instance of the bean and then wrap it with a spy.
-- When used on a field in a test class, the created spy will also be injected into that field, allowing you to interact
with it directly in your test methods. [And all of this on @SpyBean was taken from Google AI Overview, but seems legit]
*/
// 2025-11-17-EDIT: This Test Case is poorly implemented and won't work because it don't account for my backoffMs delay thing in ProcessingService.java !!!
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.main.allow-bean-definition-overriding=true")
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
    @TestConfiguration
    static class DeterministicFailHandler {
        @Bean(name="FAIL")  // FIX(?): Need to match FailHandler's @Component("FAIL")
        @Primary
        public TaskHandler detFailHandler() {
            return task -> {
                logger.info("DEBUG: ABOUT TO THROW NEW TASKPROCESSINGEXCEPTION!");
                throw new TaskProcessingException("Intentional fail for retry simulation [this time literally as part of the Integration Tests]");
            };
        }
    }

    @Test
    void failingTask_markedFailed_andRetryScheduled() {
        // 1. MAKE TASK:
        TaskEntity entity = taskService.createTask("RETRY TEST", TaskType.FAIL);
        String id = entity.getId();

        // WAIT FOR FIRST FAILURE:
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TaskEntity e = taskRepository.findById(id).orElseThrow();
            assertThat(e.getStatus()).isEqualTo(TaskStatus.QUEUED); // <-- Pretty sure this should actually check to see if it was QUEUED (to imply that requeue is coming).
            assertThat(e.getAttempts()).isGreaterThanOrEqualTo(1);
        });
        // WAIT FOR REQUEUE + SECOND ATTEMPT -> ATTEMPTS INCREASED:
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TaskEntity e = taskRepository.findById(id).orElseThrow();
            assertThat(e.getAttempts()).isGreaterThanOrEqualTo(2);
        });

        // 2. WAIT FOR TASK TO BE PROCESSED AND MARKED "FAILED":
        /*Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    TaskEntity e = taskRepository.findById(id).orElseThrow();
                    assertThat(e.getStatus()).isEqualTo(TaskStatus.FAILED);
                    assertThat(e.getAttempts()).isGreaterThanOrEqualTo(1);
                });

        // 3. VERIFY THAT RETRY SCHEDULING HAPPENED (DETECT VIA enqueueById() BEING INVOKED -- MUCH SAFER TEST):
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    verify(queueService, atLeastOnce()).enqueueById(id);
                });*/

        // wait for the attempt to be recorded (ProcessingService should claim and set attempts >=1)
        // Wait for the Task to be persisted and claimed by ProcessingService.
        /*Awaitility.await()
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
                });*/
    }
}
