package com.springqprobackend.springqpro.integration;

import com.springqprobackend.springqpro.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class TaskGraphQLIntegrationTest {

    // 2025-11-19-DEBUG:+NOTE: ADDED THE TWO CLASSES BELOW TO FIX THE I/O ERRORS I'M GETTING WHERE THREADS PROCESS POST-SHUTDOWN.
    // EDIT: THEY DON'T WORK.
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Qualifier("execService")
        public ExecutorService queueExecutorOverride() {
            return new DirectExecutorService();
        }
        @Bean
        @Qualifier("schedExec")
        public ScheduledExecutorService schedulerOverride() {
            return new ScheduledThreadPoolExecutor(1) {
                @Override
                public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                    // run immediately in tests
                    command.run();
                    return null;
                }

                @Override
                public void shutdown() {}
                @Override
                public List<Runnable> shutdownNow() { return Collections.emptyList(); }
                @Override
                public boolean isShutdown() { return false; }
                @Override
                public boolean isTerminated() { return false; }
                @Override
                public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
            };
        }
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        @Override
        public void execute(Runnable command) {
            command.run();  // run immediately, same thread.
        }
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private GraphQlTester graphQlTester;

    @LocalServerPort
    private int port;

    @Autowired
    private TaskRepository taskRepository;

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

}
