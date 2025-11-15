package com.springqprobackend.springqpro.service;

import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import com.springqprobackend.springqpro.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final ScheduledExecutorService scheduler;   // Handles micro-level retry scheduling — backoff logic for failed tasks only.
    private final QueueService queueService; // to re-enqueue by id when scheduling retries
    // Constructor(s):
    public ProcessingService(TaskRepository taskRepository, TaskHandlerRegistry handlerRegistry, ScheduledExecutorService scheduler, QueueService queueService) {
        this.taskRepository = taskRepository;
        this.handlerRegistry = handlerRegistry;
        this.scheduler = scheduler;
        this.queueService = queueService;
    }

    // Method for attempting to claim a Task and process it. This method coordinates DB-level claim and handler execution.
    // The retry doesn’t live inside the handler anymore — it’s a post-processing policy in ProcessingService. If it throws, the failure is caught in the ProcessingService try/catch block.
    public void claimAndProcess(String taskId) {
        // Load current in-DB Task snapshot (it'll be "frozen" as QUEUED until this method "claims" it):
        // NOTE: As alluded to, this is basically replacing the "Thread dequeues as processes a Task" functionality of QueueService.
        Optional<TaskEntity> snapshot = taskRepository.findById(taskId);    // NOTE: Remember this is a built-in function when you extend JPA.
        if(snapshot.isEmpty()) return;
        TaskEntity current = snapshot.get();
        // Attempt to claim: change QUEUED -> INPROGRESS (atomically w/ TaskRepository), increment attempts:
        int updated = taskRepository.transitionStatus(taskId, TaskStatus.QUEUED, TaskStatus.INPROGRESS, current.getAttempts() + 1);
        if (updated == 0) {
            return; // Claim failed: already claimed or status has been changed.
        }
        // Re-fetch the fresh TaskEntity now that we claimed it
        TaskEntity claimed = taskRepository.findById(taskId).orElse(null);
        if (claimed == null) return;

        // Convert to in-memory Task model for existing handlers (you can also change handlers to accept TaskEntity)
        /* DEBUG:+NOTE:+TO-DO: ^ This is not the best (?) Think I should adjust my TaskHandlers to accept TaskEntity too
        or maybe just have a helper method that does the conversion externally. (Maybe both). Not high priority though, get main stuff working first. */
        Task model = new Task(
                claimed.getId(),
                claimed.getPayload(),
                claimed.getType(),
                claimed.getStatus(),
                claimed.getAttempts(),
                claimed.getMaxRetries(),
                claimed.getCreatedAt() != null ? claimed.getCreatedAt() : null
        );
        // try-catch-block to do the processing:
        try {
            TaskHandler handler = handlerRegistry.getHandler(model.getType().name());
            if(handler == null) {
                handler = handlerRegistry.getHandler("DEFAULT");
            }
            handler.handle(model);
            claimed.setStatus(TaskStatus.COMPLETED);
            taskRepository.save(claimed);
        } catch(Exception ex) {
            // ProcessingService catches Task FAILs gracefully - marking it as FAILED, persisting it, and re-enqueuing it with backoff.
            claimed.setStatus(TaskStatus.FAILED);
            taskRepository.save(claimed);
            if (claimed.getAttempts() < claimed.getMaxRetries()) {
                long delayMs = computeBackoffMs(claimed.getAttempts());
                scheduler.schedule(() -> queueService.enqueueById(taskId), delayMs, TimeUnit.MILLISECONDS);
            } else {
                // permanent failure:
                logger.error("Task failed permanently. DEBUG: Come and write a more detailed case here later I barely slept.");
            }
        }
    }

    /* NOTE: In my original setup from the ProtoType phase (SpringQueue), I had fixed times set for the Task processing
    time. For Tasks that FAIL, that's not the greatest idea (static and don't adapt to retries). It's more professional
    to have something like the method below which uses "dynamic backoff." Which is better because:
    - It reduces load on the system (if many tasks fail simultaneously, you won't hammer the DB or external services repeatedly).
    - It avoids synchronized retries -- if all tasks waited 5 seconds exactly, they'd retry in lockstep (bad given new architectural overall).
    - MOST IMPORTANTLY: This mimics cloud-native retry policies (it's what AWS SQS, Kafka consumers, and Resilience4j would apply automatically).

    EDIT: It's only for retries that the exponential stuff is applied, and makes sense.
    TO-DO: Edit the method below so that computeBackOffMs() uses values from TaskHandlerProperties as a "base delay" to be used as a multiplier.
    (Best of both worlds and its' a nice thing to carry over from my prototype phase).
    */
    private long computeBackoffMs(int attempts) {
        // exponential backoff base 1000ms. apparently this is common practice in cloud stuff so might as well start it now.
        return (long) (1000 * Math.pow(2, Math.max(0, attempts - 1)));
    }
}
