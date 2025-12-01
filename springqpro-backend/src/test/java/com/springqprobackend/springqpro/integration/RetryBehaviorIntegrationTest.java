package com.springqprobackend.springqpro.integration;

import com.springqprobackend.springqpro.domain.exception.TaskProcessingException;
import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.handlers.TaskHandler;
import com.springqprobackend.springqpro.repository.TaskRepository;
import com.springqprobackend.springqpro.repository.UserRepository;
import com.springqprobackend.springqpro.service.TaskService;
import com.springqprobackend.springqpro.testcontainers.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

/* DESCRIPTION OF THIS TEST CASE:
- Ensures that a Handler that is marked to fail (FailHandler is overridden so its handle() method throws immediately)
causes ProcessingService to schedule a retry (and that attempts are incremented, which basically serves as proof).
- FailHandler is overridden so its handle() method throws immediately, this was done for deterministic test behavior.
*/

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
//@Testcontainers
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.main.allow-bean-definition-overriding=true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
class RetryBehaviorIntegrationTest extends IntegrationTestBase {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(RetryBehaviorIntegrationTest.class);

    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private UserRepository userRepository;

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

    @BeforeEach
    void cleanDb() {
        taskRepository.deleteAll();
        if (!userRepository.existsById("retrytest@example.com")) {
            userRepository.save(new com.springqprobackend.springqpro.domain.entity.UserEntity(
                    "retrytest@example.com",
                    "{noop}password"     // encoder is irrelevant: we never authenticate in this test
            ));
        }
    }

    // 2025-11-30-NOTE: Test below is architecturally outdated.
    // 2025-11-17-DEBUG: Renaming the Test name. (Checking that it's QUEUED is more accurate than checking if it's FAILED).
    //@Disabled
    @Test
    void failingTask_isRequeued_andRetryScheduled() {
        // 1. MAKE TASK:
        TaskEntity entity = taskService.createTaskForUser("RETRY TEST", TaskType.FAIL, "random_email@gmail.com");
        String id = entity.getId();

        // WAIT FOR FIRST FAILURE:
        Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            TaskEntity e = taskRepository.findById(id).orElseThrow();
            assertThat(e.getStatus()).isEqualTo(TaskStatus.QUEUED); // <-- EDIT: Pretty sure this should actually check to see if it was QUEUED and not FAILED (to imply that requeue is coming).
            assertThat(e.getAttempts()).isGreaterThanOrEqualTo(1);
        });
        // WAIT FOR REQUEUE + SECOND ATTEMPT -> ATTEMPTS INCREASED:
        Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            TaskEntity e = taskRepository.findById(id).orElseThrow();
            assertThat(e.getAttempts()).isGreaterThanOrEqualTo(2);
        });
    }
}
