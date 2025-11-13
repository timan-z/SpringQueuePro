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
*/

@Service
public class ProcessingService {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);
    private final TaskRepository taskRepository;
    private final TaskHandlerRegistry handlerRegistry;
    private final ScheduledExecutorService scheduler;
    private final QueueService queueService; // to re-enqueue by id when scheduling retries
    // Constructor(s):
    public ProcessingService(TaskRepository taskRepository, TaskHandlerRegistry handlerRegistry, ScheduledExecutorService scheduler, QueueService queueService) {
        this.taskRepository = taskRepository;
        this.handlerRegistry = handlerRegistry;
        this.scheduler = scheduler;
        this.queueService = queueService;
    }

    // Method for attempting to claim a Task and process it. This method coordinates DB-level claim and handler execution.
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
    private long computeBackoffMs(int attempts) {
        // exponential backoff base 1000ms. apparently this is common practice in cloud stuff so might as well start it now.
        return (long) (1000 * Math.pow(2, Math.max(0, attempts - 1)));
    }
}
