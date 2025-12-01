package com.springqprobackend.springqpro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/* ExecutorConfig.java
--------------------------------------------------------------------------------------------------
[HISTORY]:
Early prototypes created thread pools directly inside QueueService. As more
parallelism was added (ProcessingService, retry scheduling, GraphQL execution),
the need for controlled, centralized executor configuration became clear.
ExecutorConfig externalized thread pool creation and tuning.

[CURRENT ROLE]:
Provides:
  - primary ExecutorService (fixed thread pool)
  - ScheduledExecutorService for retry/backoff scheduling
Used by:
  - QueueService (submit tasks)
  - ProcessingService (delayed retries)

[FUTURE WORK]:
CloudQueue may adopt:
  - managed executors
  - Quartz scheduling
  - asynchronous dispatcher microservices
--------------------------------------------------------------------------------------------------
*/

// 2025-11-30-NOTE: Ignore the two comment blocks below.
/* NOTE-TO-SELF: Now that I'm wiring in ExecutorService and ScheduledExecutorService, there are definitely
adjustments that I need to make to my existing Unit Tests that I'll need to make before writing Integration Tests! (DEBUG: REMEMBER).
*/
/* 2025-11-14-DEBUG:+NOTE(S)-TO-SELF:
Originally, in QueueService, during the prototype phase of this project, I had ScheduledExecutor declared manually.
That's fine for prototypes, but in Spring Boot apps with persistence and long-lived threads (my project target), manual
executors create hidden lifecycle and monitoring issues. With the Executors being injected,
- Life Cycle is now managed: Spring will gracefully shut down the executor on app close - no lingering threads. (So maybe I do just remove the @PreDestory thing?)
- Thread-Pool Reuse: One Scheduled Executor is now shared across components (e.g., QueueService and ProcessingService).
This is better and more professional so yeah.
*/
@Configuration
public class ExecutorConfig {
    // Field(s):
    private QueueProperties props;  // NOTE: QueueProperties is in the same package so no import needed (DEBUG: May change down the line?).

    public ExecutorConfig(QueueProperties props) { this.props = props; }

    @Bean("execService")
    public ExecutorService taskExecutor() {
       return Executors.newFixedThreadPool(props.getMainExecWorkerCount(), r -> {
           Thread t = new Thread(r);
           t.setName("QS-Worker-" + t.getId());
           return t;
       });
    }

    @Bean("schedExec")
    public ScheduledExecutorService taskScheduler() {
        return Executors.newScheduledThreadPool(props.getSchedExecWorkerCount());
    }
}
