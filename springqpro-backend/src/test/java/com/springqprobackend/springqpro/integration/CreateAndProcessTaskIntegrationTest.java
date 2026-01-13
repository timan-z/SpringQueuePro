package com.springqprobackend.springqpro.integration;

import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.repository.TaskRepository;
import com.springqprobackend.springqpro.testcontainers.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

// 2025-11-30: This Integration Test is testing outdated architecture. Keeping it for now because it should be noted in my documentation.

/* NOTE(S)-TO-SELF:
- @Testcontainers enables Container Lifecycle Support in JUnit.
- @SpringBootTest is "useful when you need to bootstrap the entire container. This annotation works by
creating the ApplicationContext that will be utilized in our tests." (Arguments are pretty straightforward).
- @Container provides a Docker container to manage for the Test Class.
- @DynamicPropertySource is an annotation that can be applied to methods in Integration Test classes that need to
register dynamic properties to be added to the set of PropertySources in the Environment for an ApplicationContext loaded for an Int Test.
- TestRestTemplate is a utility class designed for Int Testing of RESTful services. It extends RestTemplate and
provides a convenient way to make HTTP Requests within my tests (esp when using an embedded server w/ @SpringBootTest).
^ [This was the definition Google AI Overview provided, but sounds about right].
*/

/* TO-DO:+NOTE: Maybe instead of using Awaitility, see if I can do something similar as to before w/ manually configured
Executors to skip the processing time? I have no idea if I can do that with Integration Tests. Honestly, probably not, but look into it.
*/

/* TO-DO:+NOTE:+GPT Suggestion: I could have a "Scheduled Queue Scanner" that finds stuck QUEUED Tasks every X seconds:
"@Scheduled(fixedDelay = 2000)
public void sweepQueuedTasks() {
    List<String> ids = taskRepository.findQueuedTaskIds();
    ids.forEach(queueService::enqueueById);
}" <-- That is what Kafka consumers, SQS workers, and Airflow do. (So this is a more cloud-native solution, but figure things out deeper later).
*/

/* This method here basically:
1. Boots up the Spring context with a PostgreSQL Testcontainer.
2. Calls the REST endpoint w/ TestRestTemplate to create a Task (this will persist the TaskEntity and enqueue w/ TaskService).
3. Waits (w/ Awaitility) for the DB row to become COMPLETED verifying that the ProcessingService ran and handler executed, status persisted.
- Uses Awaitility to wait for ProcessingService's asynchronous processing.
*/
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@Tag("disable_temp")
class CreateAndProcessTaskIntegrationTest extends IntegrationTestBase {
    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void cleanDb() {
        taskRepository.deleteAll();
    }

    @Disabled("Outdated architecture — will fix later")
    @Test
    void createTask_isPersisted_andEventuallyProcessed() {
        // Starting w/ creating Task via REST endpoint (my ProducerController's POST /enqueue):
        Map<String, Object> req = Map.of("payload", "integration-test", "type", "EMAIL");
        ResponseEntity<Map> response = rest.postForEntity("/api/enqueue", req, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful());

        /* After the Request is enqueued, that Task is saved to the DataBase and sent to the in-memory QueueService pool.
        To make sure that the Task was persisted, we can check the most recent row in our DB: */
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(()-> {
                    // No point if the Task Repository is empty (no rows).
                    Iterable<TaskEntity> all = taskRepository.findAll();
                    assertThat(all).isNotEmpty();
                });
        // Retrieving the first row:
        TaskEntity entity = taskRepository.findAll().iterator().next();
        assertThat(entity.getPayload()).isEqualTo("integration-test");
        // Wait for processing to finish
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(300))
                .until(() -> {
                    Optional<TaskEntity> refreshed = taskRepository.findById(entity.getId());
                    return refreshed.map(e -> e.getStatus() == TaskStatus.COMPLETED || e.getStatus() == TaskStatus.FAILED).orElse(false);
                });
    }
}
