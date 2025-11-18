package com.springqprobackend.springqpro.integration;

import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.repository.TaskRepository;
import com.springqprobackend.springqpro.service.ProcessingService;
import com.springqprobackend.springqpro.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ProcessingConcurrencyIntegrationTest {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(ProcessingConcurrencyIntegrationTest.class);

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
    @Autowired
    private ProcessingService processingService;

    @Test
    void twoThreads_tryToClaim_sameTask_onlyOneSucceeds() throws InterruptedException, ExecutionException {
        TaskEntity entity = taskService.createTask("concurrency-test", TaskType.EMAIL);
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
