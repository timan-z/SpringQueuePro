package com.springqprobackend.springqpro.integration;

import java.time.Instant;
import java.util.List;

import com.springqprobackend.springqpro.security.dto.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import com.springqprobackend.springqpro.domain.entity.UserEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.repository.TaskRepository;
import com.springqprobackend.springqpro.repository.UserRepository;
import com.springqprobackend.springqpro.security.JwtUtil;
import com.springqprobackend.springqpro.testcontainers.IntegrationTestBase;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/* 2025-11-17-NOTE(S)-TO-SELF:
- GraphQlTester is Spring's testing utility for GraphQL endpoints.
It's the GraphQL equivalent of [MockMvc for REST], [WebTestClient for reactive HTTP], and [TestRestTemplate for REST integration tests].
GraphQlTester lets you send queries/mutations, inspect fields in the GraphQL response, deserialize response fields into
Java objects (like String, Integer, custom stuff) and assert values using a fluent, chainable API.
*/
/* 2025-11-19-NOTE:
THE TESTS WORK BUT I KEEP GETTING THIS ERROR BELOW:
"2025-11-19T12:03:22.382-05:00  WARN 1776 --- [springqpro] [   QS-Worker-99] o.h.engine.jdbc.spi.SqlExceptionHelper   : SQL Error: 0, SQLState: 08006
2025-11-19T12:03:22.383-05:00 ERROR 1776 --- [springqpro] [   QS-Worker-99] o.h.engine.jdbc.spi.SqlExceptionHelper   : An I/O error occurred while sending to the backend.
2025-11-19T12:03:22.387-05:00  INFO 1776 --- [springqpro] [ionShutdownHook] j.LocalContainerEntityManagerFactoryBean : Closing JPA EntityManagerFactory for persistence unit 'default'
2025-11-19T12:03:22.392-05:00  INFO 1776 --- [springqpro] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2025-11-19T12:03:22.401-05:00  INFO 1776 --- [springqpro] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed."

I know what the error is. So, basically, my tests succeed BUT my Worker Threads are still active after my
Test completes (and succeeds) and after the Spring test context begins shutting down. So these Threads are
trying to invoke my atomic SQL operations inside ProcessingService.java inside my Postgres container when
I've already shut it down (Testcontainers has closed it). So, my Worker Threads attempt SQL operations
only to see the SQL and I/O error. (TL;DR: My async queue processing system is still running during shutdown).

I NEED TO FINISH THIS PROJECT BEFORE THE END OF NOVEMBER, AND THIS IS SUCH A HASSLE I AM GOING TO LEAVE IT FOR NOW
BECAUSE IT'S JUST A TEST THING AND I REALLY WANT TO FINISH THIS PROJECT BEFORE THE LAST WEEK OF NOVEMBER IF POSSIBLE.
I DON'T HAVE TIME FOR THIS.
TO-DO: FIX THIS PROBLEM PLEASE FOR THE LOVE OF GOD.
*/
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@Testcontainers
//@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.queue.processing.enabled=false",
                "spring.task.scheduling.enabled=false"
        }
)
@ActiveProfiles("test")
class TaskGraphQLIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_EMAIL = "graphql-test@example.com";
    private static final String PASSWORD = "password";

    @BeforeEach
    void setup() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    // Test #1: createTask + fetch by id
    @Test
    void createTask_thenQueryById_returnsCorrectTask() {
        AuthResponse auth = registerAndLogin(TEST_EMAIL, PASSWORD);

        String createMutation = """
                mutation {
                  createTask(input: {
                    payload: "send-an-email"
                    type: EMAIL
                  }) {
                    id
                    payload
                    type
                    status
                  }
                }
                """;

        AtomicReference<String> taskIdRef = new AtomicReference<>();

        graphQLWithToken(auth.accessToken(), createMutation)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.createTask.id")
                .value(id -> taskIdRef.set((String) id))
                .jsonPath("$.data.createTask.payload").isEqualTo("send-an-email")
                .jsonPath("$.data.createTask.type").isEqualTo("EMAIL")
                .jsonPath("$.data.createTask.status").isEqualTo("QUEUED");

        String taskId = taskIdRef.get();

        String query = """
                query {
                  task(id: "%s") {
                    id
                    payload
                    type
                    status
                  }
                }
                """.formatted(taskId);

        graphQLWithToken(auth.accessToken(), query)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.task.id").isEqualTo(taskId)
                .jsonPath("$.data.task.payload").isEqualTo("send-an-email")
                .jsonPath("$.data.task.type").isEqualTo("EMAIL");
    }

    // Test #2: updateTask reflects in DB + query
    @Test
    void updateTask_updatesDatabase_andQueryReflectsChange() {
        AuthResponse auth = registerAndLogin(TEST_EMAIL, PASSWORD);

        TaskEntity task = new TaskEntity(
                "Task-Update-1",
                "original payload",
                TaskType.EMAIL,
                TaskStatus.QUEUED,
                0,
                3,
                Instant.now(),
                TEST_EMAIL
        );
        taskRepository.save(task);

        String mutation = """
                mutation {
                  updateTask(input: {
                    id: "%s"
                    status: COMPLETED
                    attempts: 1
                  }) {
                    id
                    status
                    attempts
                  }
                }
                """.formatted(task.getId());

        graphQLWithToken(auth.accessToken(), mutation)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.updateTask.status").isEqualTo("COMPLETED")
                .jsonPath("$.data.updateTask.attempts").isEqualTo(1);

        TaskEntity updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(updated.getAttempts()).isEqualTo(1);
    }

    // Test #3: deleteTask removes entity
    @Test
    void deleteTask_removesTaskFromDatabase() {
        AuthResponse auth = registerAndLogin(TEST_EMAIL, PASSWORD);

        TaskEntity task = new TaskEntity(
                "Task-Delete-1",
                "to delete",
                TaskType.EMAIL,
                TaskStatus.QUEUED,
                0,
                3,
                Instant.now(),
                TEST_EMAIL
        );
        taskRepository.save(task);

        String mutation = """
                mutation {
                  deleteTask(id: "%s")
                }
                """.formatted(task.getId());

        graphQLWithToken(auth.accessToken(), mutation)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.deleteTask").isEqualTo(true);

        assertThat(taskRepository.findById(task.getId())).isEmpty();
    }

    // Test #4: filter tasks by status
    @Test
    void tasksQuery_filtersByStatus() {
        AuthResponse auth = registerAndLogin(TEST_EMAIL, PASSWORD);

        taskRepository.saveAll(List.of(
                new TaskEntity("t1", "a", TaskType.EMAIL, TaskStatus.QUEUED, 0, 3, Instant.now(), TEST_EMAIL),
                new TaskEntity("t2", "b", TaskType.EMAIL, TaskStatus.COMPLETED, 1, 3, Instant.now(), TEST_EMAIL),
                new TaskEntity("t3", "c", TaskType.SMS, TaskStatus.QUEUED, 0, 3, Instant.now(), TEST_EMAIL)
        ));

        String query = """
                query {
                  tasks(status: QUEUED) {
                    id
                  }
                }
                """;

        graphQLWithToken(auth.accessToken(), query)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.tasks.length()").isEqualTo(2);
    }

    // Test #5: filter tasks by type
    @Test
    void tasksTypeQuery_filtersByType() {
        AuthResponse auth = registerAndLogin(TEST_EMAIL, PASSWORD);

        taskRepository.saveAll(List.of(
                new TaskEntity("t1", "n1", TaskType.NEWSLETTER, TaskStatus.QUEUED, 0, 3, Instant.now(), TEST_EMAIL),
                new TaskEntity("t2", "n2", TaskType.NEWSLETTER, TaskStatus.COMPLETED, 1, 3, Instant.now(), TEST_EMAIL),
                new TaskEntity("t3", "s1", TaskType.SMS, TaskStatus.QUEUED, 0, 3, Instant.now(), TEST_EMAIL)
        ));

        String query = """
                query {
                  tasksType(type: NEWSLETTER) {
                    id
                  }
                }
                """;

        graphQLWithToken(auth.accessToken(), query)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.tasksType.length()").isEqualTo(2);
    }
}
