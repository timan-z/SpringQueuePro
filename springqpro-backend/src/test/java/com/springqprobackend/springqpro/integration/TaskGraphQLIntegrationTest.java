package com.springqprobackend.springqpro.integration;

import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.repository.TaskRepository;
import com.springqprobackend.springqpro.testcontainers.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.time.Instant;

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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TaskGraphQLIntegrationTest extends IntegrationTestBase {

    private GraphQlTester graphQlTester;

    @LocalServerPort
    private int port;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void init() {
        taskRepository.deleteAll();
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/graphql")
                .exchangeStrategies(
                        ExchangeStrategies.builder()
                                .codecs(c -> c.defaultCodecs().maxInMemorySize(5_000_000))
                                .build()
                )
                .build();
        this.graphQlTester = HttpGraphQlTester.create(client);
    }

    // Test #1 - Create Task, Query the Task, then verify its Status, Type, and Payload:
    @Test
    void taskCreation_succeeds_isRetrieved() {
        String mutation = """
                mutation {
                    createTask(input:{
                        payload:"send-an-email"
                        type:EMAIL
                    }) {
                        id
                        payload
                        type
                        status
                        attempts
                        maxRetries
                    }
                }
                """;
        /*2025 - 11 - 17 - NOTE(S) - TO - SELF:+DEBUG:
        -graphQlTester.document(mutation) prepares a GraphQL mutation with String mutation, which is just a String
        containing
        a proper GraphQL mutation obv.So graphQlTester runs it by running my actual Spring Boot app(which runs my real
                GraphQL controller, my real service layer, and uses the real database(Testcontainers in this
        case)).
        - .execute() clearly runs it and you can inspect the fields.*/
        var created = graphQlTester.document(mutation)
            .execute()
            .path("createTask.payload").entity(String.class).isEqualTo("send-an-email")
            .path("createTask.type").entity(String.class).isEqualTo("EMAIL")
            .path("createTask.status").entity(String.class).isEqualTo("QUEUED");
        // Extract the id from the same GraphQL mutation:
        String id = graphQlTester.document(mutation)
                .execute()
                .path("createTask.id").entity(String.class).get();
        // Search for that Task w/ a GraphQL query:
        String query = """
                query {
                    task(id: "%s") {
                        id
                        payload
                        type
                        status
                    }
                }
                """.formatted(id);
        graphQlTester.document(query)
                .execute()
                .path("task.payload").entity(String.class).isEqualTo("send-an-email")
                .path("task.type").entity(String.class).isEqualTo("EMAIL");
    }

    // Test #2 - Update Task (Partially), Verify Change in DB, Query Returns Updated:
    @Test
    void updateTask_verifyDBChange_retrieveByQuery() {
        TaskEntity entity = new TaskEntity(
                "Task-ArbitraryTaskId",
                "Send an email",
                TaskType.EMAIL,
                TaskStatus.QUEUED,
                0,
                3,
                Instant.now()
        );
        taskRepository.save(entity);
        // Manually change the status of entity to COMPLETED and increment attempts:
        // NOTE: When you want to specify integer in mutation, put %d -- "%d" will return you a integer wrapped in a String (aka just a String)
        String mutation = """
                mutation {
                    updateTask(input:{
                        id:"%s",
                        status:COMPLETED,
                        attempts:%d
                    }) {
                        id
                        status
                        attempts
                    }
                }
                """.formatted(entity.getId(), entity.getAttempts()+1);
        graphQlTester.document(mutation).execute()
                .path("updateTask.status").entity(String.class).isEqualTo("COMPLETED")
                .path("updateTask.attempts").entity(Integer.class).isEqualTo(1);
        // Verify that the change happened in the DataBase w/ taskRepository:
        TaskEntity getEntity = taskRepository.findById(entity.getId()).orElseThrow();
        assert getEntity.getStatus() == TaskStatus.COMPLETED;
        assert getEntity.getAttempts() == entity.getAttempts() + 1;
    }

    // Test #3 - Delete Task, Verify Change in DB, Query Returns NULL (or whatever):
    @Test
    void deleteTask_verifyDBChange_queryRetrieveNull() {
        TaskEntity entity = new TaskEntity(
                "Task-ArbitraryTaskId",
                "Send an email",
                TaskType.EMAIL,
                TaskStatus.QUEUED,
                0,
                3,
                Instant.now()
        );
        taskRepository.save(entity);
        String mutation = """
                mutation {
                    deleteTask(id:"%s")
                }
                """.formatted(entity.getId());
        graphQlTester.document(mutation)
                .execute()
                .path("deleteTask").entity(Boolean.class).isEqualTo(true);
        assert taskRepository.findById(entity.getId()).isEmpty();
    }

    // Test #4 - Being able to filter Tasks w/ Status:
    @Test
    void filterTasks_byStatusQuery_returnsCorrectList() {
        // Task #1:
        taskRepository.save(
                new TaskEntity("Task-ArbitraryTaskId-1","Send an email", TaskType.EMAIL, TaskStatus.QUEUED, 0, 3, Instant.now())
        );
        // Task #2:
        taskRepository.save(
                new TaskEntity("Task-ArbitraryTaskId-2", "Send an email 2", TaskType.EMAIL, TaskStatus.COMPLETED, 1, 3, Instant.now())
        );
        // Task #3:
        taskRepository.save(
                new TaskEntity("Task-ArbitaryTaskId-3", "Send an SMS or whatever", TaskType.SMS, TaskStatus.QUEUED, 0, 3, Instant.now())
        );
        String query = """
                query {
                    tasks(status: QUEUED) {
                        id
                        payload
                        status
                    }
                }
                """;
        graphQlTester.document(query)
                .execute()
                .path("tasks").entityList(TaskEntity.class).hasSize(2);
    }

    // Test #5 - Being able to filter Tasks w/ Type:
    @Test
    void filterTasks_byTypeQuery_returnsCorrectList() {
        // Task #1:
        taskRepository.save(
                new TaskEntity("Task-ArbitraryTaskId-1","Do something NEWSLETTER related, I don't know.", TaskType.NEWSLETTER, TaskStatus.QUEUED, 0, 3, Instant.now())
        );
        // Task #2:
        taskRepository.save(
                new TaskEntity("Task-ArbitraryTaskId-2", "Do something NEWSLETTER related, I don't know 2.", TaskType.NEWSLETTER, TaskStatus.COMPLETED, 1, 3, Instant.now())
        );
        // Task #3:
        taskRepository.save(
                new TaskEntity("Task-ArbitraryTaskId-3", "Send an SMS or whatever", TaskType.SMS, TaskStatus.QUEUED, 0, 3, Instant.now())
        );
        // Task #4:
        taskRepository.save(
                new TaskEntity("Task-ArbitraryTaskId-4", "Do something NEWSLETTER related, I don't know 3.", TaskType.NEWSLETTER, TaskStatus.QUEUED, 0, 3, Instant.now())
        );
        String query = """
                query {
                    tasksType(type: NEWSLETTER) {
                        id
                        payload
                        status
                    }
                }
                """;
        graphQlTester.document(query)
                .execute()
                .path("tasksType").entityList(TaskEntity.class).hasSize(3);
    }
}
