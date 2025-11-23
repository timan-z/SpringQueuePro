package com.springqprobackend.springqpro.service;

import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.mapper.TaskMapper;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import com.springqprobackend.springqpro.redis.RedisDistributedLock;
import com.springqprobackend.springqpro.redis.TaskRedisRepository;
import com.springqprobackend.springqpro.repository.TaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* NOTE-TO-SELF: - [2025-11-13 EDIT]:
This is a new @Service file that will contain the transactional, DB-focused logic for
claiming and persisting a task's lifecycle. It'll be called from a QueueService worker thread
(but the DB updates happen inside @Transactional methods here).
Basically, this file is the transactional boundary for claiming and persisting TaskEntity changes.

In the overhaul I'm doing to harden the persistence flow, some of the logic originally contained in
QueueService more or less gets migrated over here.
EDIT: Actually, a lot of QueueService is getting moved over here.
******************************************************************
2025-11-14-DEBUG:+NOTE(S)-TO-SELF:
*See notes appended to QueueService.java on which these notes build upon*
ProcessingService is the bridge between the Domain Layer (my in-memory Queue / QueueService + my Handlers) and the Persistence Layer (Postgres via Spring Data JPA).
This lets me treat the Queue as ephemeral and the DB as authoritative -- which is key in professional distributed/restartable systems.
- ProcessingService is what now ensures that state transitions (PENDING → PROCESSING → COMPLETED/FAILED) are persisted atomically and safely.
Really, ProcessingService.java, it's almost like a tool that's used by QueueService.java (so makes sense a lot of the
functionality originally there is now ported over here -- great for decoupling but also moving towards my intended Event-Driven setup).

ProcessingService decoupling:
- Task Claiming/Selection will no longer be in QueueService -> this has been moved to ProcessingService.claimAndProcess()
- Task Processing was originally done by the Worker Threads in QueueService -> now they will be done in ProcessingService.
- Task Status Updates were originally quite scattered (done in QueueService and individual Handlers) -> now centralized in ProcessingService w/ taskRepository.save()
- Retry Scheduling, which was originally in QueueService as its own method -> now done in ProcessingService w/ Scheduler + Backoff Time

The try-catch block inside of method claimAndProcess() replaces the original low-level retry logic that was previously being
done in my Handlers (e.g., FailHandler).
- Task Status updating is now centralized by way of repository instead of Handler.
- The Scheduler in ProcessingService now does the retry scheduling originally done by QueueService.
- Instead of fixed variable sleep times for retry (failed tasks), I have computeBackOffMs at the bottom. More on a comment appended there.

Notes on ProcessingService.java:
- claimAndProcess does the transitionStatus claim (atomic).
- When claim succeeds, it reloads the entity and processes by invoking the handler through TaskHandlerRegistry.
- Saves final state to DB and schedules retry if applicable.
- ProcessingService guarantees that DB writes are serialized and safe via optimistic locking (@Version).
- ProcessingService can use optimistic locking (@Version) to ensure only one worker processes a given task.

Again, what ProcessingService does now:
- Direct DB saves (taskRepository.save)
- Retry scheduling and backoff
- Task claiming
- Persistence of lifecycle changes (status, attempt count)

EVERYTHING THAT WAS SO TIGHTLY COUPLED IN QueueService IS NOW DELEGATED HERE!
NOW:
QueueService = how to execute work.
ProcessingService = how to handle outcomes and persist them.
AND:
QueueService = a delivery system for work units — a producer/consumer dispatcher.
ProcessingService = owns state transitions and retry semantics.
Worker threads now simply execute processing calls safely and concurrently, reading from durable state.
This is a textbook event-driven architecture with persistence-backed coordination.
THAT'S WHAT YOU FIND IN PROFESSIONAL-GRADE ARCHITECTURE MODELS LIKE Spring Batch, Celery, Sidekiq, and so on.
*/

@Service
public class ProcessingService {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);
    private final TaskRepository taskRepository;
    private final TaskHandlerRegistry handlerRegistry;
    private final TaskMapper taskMapper;
    private final ScheduledExecutorService scheduler;   // Handles micro-level retry scheduling — backoff logic for failed tasks only.
    private final QueueService queueService; // to re-enqueue by id when scheduling retries
    private final RedisDistributedLock redisLock;   // 2025-11-23-DEBUG: REDIS INTEGRATION PHASE!
    private final TaskRedisRepository cache;    // 2025-11-23-DEBUG: Refactoring for TaskRedisRepository.java

    @PersistenceContext
    private EntityManager em;   // 2025-11-17-DEBUG: Need this for flush() and refresh(), which is professionally commonplace when working with Spring to enforce ordering.

    // Constructor(s):
    @Lazy
    public ProcessingService(TaskRepository taskRepository, TaskHandlerRegistry handlerRegistry, TaskMapper taskMapper, @Qualifier("schedExec") ScheduledExecutorService scheduler, QueueService queueService, RedisDistributedLock redisLock, TaskRedisRepository cache) {
        this.taskRepository = taskRepository;
        this.handlerRegistry = handlerRegistry;
        this.taskMapper = taskMapper;
        this.scheduler = scheduler;
        this.queueService = queueService;
        this.redisLock = redisLock;
        this.cache = cache;
    }

    // 2025-11-23-DEBUG: OVERHAULING MY claimAndProcess METHOD LOGIC [BELOW]:
    // Method for attempting to claim a Task and process it. This method coordinates DB-level claim and handler execution.
    // The retry doesn’t live inside the handler anymore — it’s a post-processing policy in ProcessingService. If it throws, the failure is caught in the ProcessingService try/catch block.
    @Transactional  // <-- forgot this, it should 100% be here.
    public void claimAndProcess(String taskId) {
        logger.info("[ProcessingService] starting claimAndProcess for {}", taskId);
        // Load current in-DB Task snapshot (it'll be "frozen" as QUEUED until this method "claims" it):
        // NOTE: As alluded to, this is basically replacing the "Thread dequeues as processes a Task" functionality of QueueService.
        Optional<TaskEntity> snapshot = taskRepository.findById(taskId);    // NOTE: Remember this is a built-in function when you extend JPA.
        if(snapshot.isEmpty()) return;
        TaskEntity current = snapshot.get();
        // Attempt to claim: change QUEUED -> INPROGRESS (atomically w/ TaskRepository), increment attempts:
        logger.info("[ProcessingService] attempting transition for {} attempts {}", taskId, current.getAttempts() + 1);
        int updated = taskRepository.transitionStatus(taskId, TaskStatus.QUEUED, TaskStatus.INPROGRESS, current.getAttempts() + 1);
        logger.info("[ProcessingService] transition returned count={}", updated);
        if (updated == 0) {
            logger.warn("[ProcessingService] claim for {} failed (transition returned 0) — likely status mismatch", taskId);
            return; // Claim failed: already claimed or status has been changed.
        }

        em.flush();
        em.refresh(current);

        // Re-fetch the fresh TaskEntity now that we claimed it
        TaskEntity claimed = taskRepository.findById(taskId).orElse(null);
        if (claimed == null) return;

        // 2025-11-23-DEBUG: OVERHAULING MY claimAndProcess METHOD LOGIC [1 - BELOW]:
        String lockKey = "task:lock:" + taskId;
        String token = redisLock.tryLock(lockKey, 2000);    // 2025-11-23-DEBUG:+TO-DO: Going to use 2000 for the processing time (that's what's in application.yml I think).
        if(token == null) {
            // NOTE: REDIS LOCK NOT WORKING MEANS, FOR SAFETY, I SHOULD SET STATUS OF TASK BACK TO QUEUED SO OTHER CONSUMERS CAN GET IT:
            taskRepository.transitionStatus(taskId, TaskStatus.INPROGRESS, TaskStatus.QUEUED, claimed.getAttempts());
            return;
        }
        // 2025-11-23-DEBUG: OVERHAULING MY claimAndProcess METHOD LOGIC [1 - ABOVE].

        // Convert to in-memory Task model for existing handlers (you can also change handlers to accept TaskEntity)
        // NOTE: Considered proper DDD Principle to NOT have Persistence Objects mix with my Handlers!
        Task model = taskMapper.toDomain(claimed);

        // try-catch-block to do the processing:
        try {
            TaskHandler handler = handlerRegistry.getHandler(model.getType().name());
            if(handler == null) handler = handlerRegistry.getHandler("DEFAULT");
            handler.handle(model);
            model.setStatus(TaskStatus.COMPLETED);
            taskMapper.updateEntity(model, claimed);    // 2025-11-15-EDIT: ADDED THIS!
            TaskEntity persisted = taskRepository.save(claimed);
            cache.put(claimed); // 2025-11-23-DEBUG: REFACTORING FOR TaskRedisRepository.java
            logger.info("[ProcessingService] after save - id: {}, status: {}, attempts: {}, version: {}", persisted.getId(), persisted.getStatus(), persisted.getAttempts(), persisted.getVersion());
        } catch(Exception ex) {
            // ProcessingService catches Task FAILs gracefully - marking it as FAILED, persisting it, and re-enqueuing it with backoff.
            // 2025-11-17-DEBUG: Actually have no idea what I was doing here.
            model.setStatus(TaskStatus.FAILED);
            taskMapper.updateEntity(model, claimed);
            taskRepository.save(claimed);
            cache.put(claimed); // 2025-11-23-DEBUG: REFACTORING FOR TaskRedisRepository.java

            em.flush();
            em.refresh(claimed);

            if (claimed.getAttempts() < claimed.getMaxRetries()) {
                long delayMs = computeBackoffMs(claimed.getAttempts());
                /* 2025-11-17-DEBUG: OKAY, it looks like I spotted a massive architectural flaw inside my processAndClaim() logic.
                When a Thread tries to claim a Task, it does this: int updated = taskRepository.transitionStatus(taskId, TaskStatus.QUEUED, TaskStatus.INPROGRESS, current.getAttempts() + 1);,
                but that's the thing -- it expects the persisted Task to be of type QUEUED, but the problem, when I re-enqueue a Task, I never
                set the Status back to QUEUED; it remains FAILED so this method simply cannot run properly. That's why my Integration Test re-enqueing keeps messing up. */
                int requeued = taskRepository.transitionStatusSimple(taskId, TaskStatus.FAILED, TaskStatus.QUEUED);
                logger.info("[ProcessingService] requeue DB update for {} returned {}", taskId, requeued);
                scheduler.schedule(() -> queueService.enqueueById(taskId), delayMs, TimeUnit.MILLISECONDS);
            } else {
                // permanent failure:
                logger.error("Task failed permanently. DEBUG: Come and write a more detailed case here later I barely slept.");
            }
        } finally {
          redisLock.unlock(lockKey, token); // 2025-11-23-DEBUG: LAST ADDITION FOR TaskRedisRepository.java REFACTORING PHASE.
        }
        logger.info("[ProcessingService] finishing claimAndProcess for {} -> status now {}", taskId, claimed.getStatus());
    }
    // 2025-11-23-DEBUG: OVERHAULING MY claimAndProcess METHOD LOGIC [ABOVE].

    /* NOTE: In my original setup from the ProtoType phase (SpringQueue), I had fixed times set for the Task processing
    time. For Tasks that FAIL, that's not the greatest idea (static and don't adapt to retries). It's more professional
    to have something like the method below which uses "dynamic backoff." Which is better because:
    - It reduces load on the system (if many tasks fail simultaneously, you won't hammer the DB or external services repeatedly).
    - It avoids synchronized retries -- if all tasks waited 5 seconds exactly, they'd retry in lockstep (bad given new architectural overall).
    - MOST IMPORTANTLY: This mimics cloud-native retry policies (it's what AWS SQS, Kafka consumers, and Resilience4j would apply automatically).

    EDIT: It's only for retries that the exponential stuff is applied, and makes sense.
    TO-DO: Edit the method below so that computeBackOffMs() uses values from TaskHandlerProperties as a "base delay" to be used as a multiplier.
    (Best of both worlds and it's a nice thing to carry over from my prototype phase).
    */
    private long computeBackoffMs(int attempts) {
        // exponential backoff base 1000ms. apparently this is common practice in cloud stuff so might as well start it now.
        return (long) (1000 * Math.pow(2, Math.max(0, attempts - 1)));
    }
}
