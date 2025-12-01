package com.springqprobackend.springqpro.integration;

import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.redis.RedisDistributedLock;
import com.springqprobackend.springqpro.repository.TaskRepository;
import com.springqprobackend.springqpro.service.ProcessingService;
import com.springqprobackend.springqpro.service.TaskService;
import com.springqprobackend.springqpro.testcontainers.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/* DESCRIPTION OF THIS TEST CASE:
- Simulates two concurrent threads trying to claim the same persisted Task.
- Verifies that only one claim succeeds by asserting attempts is 1 after concurrent calls
(the transitionStatus pattern should prevent double-claim).
- Basically checks the atomicity of the DB-level Task claim (transitionStatus(...) mainly)
and checks the functionality I have in place to make sure that a Task cannot be simultaneously processed.
*/
/* 2025-11-17-NOTE:
These three Integration test that I have (ProcessingConcurrencyIntegrationTest.java, CreateAndProcessTaskIntegrationTest.java,
and RetryBehaviorIntegrationTest.java) are broad enough to cover the core functionality of my ProcessingService.java-related
architectural overhaul.
[Granted, here are some other things I can write tests for when I'm not on a time crunch]:
*More so tests to write under this new architectural overhaul*
Task Lifecycle Tests:
- Test Task Transitions across all handlers (EMAIL, NEWSLETTER, SMS, and so on).
- Test Handler-induced InterruptException produces correct FAILED and retry behavior.
QueueService behavior:
- Test enqueuing with delay, ensuring delayed tasks will only start after minimum time.
- Test that multiple delayed tasks do not block each other.
Persistence + Mapping layer:
- Test Task -> TaskEntity -> Task mapping round-trip (ensure payload, timestamps, and attempts to remain consistent).
Test queries:
- findTop5ByStatusOrderByCreatedAtAsc <-- stuff like this
Shutdown/Restart behavior:
- Maybe better off once I implement Redis.
- Making sure persisted tasks still resume processing on application restart.
GRAPHQL-RELATED QUERIES:
- Pagination Queries
- Query for Tasks by Status
- Mutation for creating tasks
- Testing n+1 fetches mitigated by DataLoader (maybe if integrated).
*/
//@Testcontainers
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProcessingConcurrencyIntegrationTest extends IntegrationTestBase {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(ProcessingConcurrencyIntegrationTest.class);
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private ProcessingService processingService;
    @Autowired
    private RedisDistributedLock redisLock;

    @BeforeEach
    void cleanDb() {
        taskRepository.deleteAll();
    }

    @Test
    void twoThreads_tryToClaim_sameTask_onlyOneSucceeds() throws InterruptedException, ExecutionException {
        TaskEntity entity = taskService.createTaskForUser("concurrency-test", TaskType.EMAIL, "random_email@gmail.com");
        String id = entity.getId();
        ExecutorService esDummy = Executors.newFixedThreadPool(2);
        Callable<Void> c = () -> {
            processingService.claimAndProcess(id);
            return null;
        };
        Future<Void> f1 = esDummy.submit(c);
        Future<Void> f2 = esDummy.submit(c);
        f1.get();
        f2.get();
        // reload:
        TaskEntity reloaded = taskRepository.findById(id).orElseThrow();
        // ATTEMPTS SHOULD BE 1.
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isNotNull();   // No double-claiming anomalies or corrupted states.
        esDummy.shutdown();
    }
}
