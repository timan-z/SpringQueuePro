package com.springqprobackend.springqpro.runtime;

import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Worker.java (DEPRECATED)
--------------------------------------------------------------------------------------------------
[HISTORY]:
This is the file that defined the Prototype Thread Worker that would be submitted by the ExecutorService
inside QueueService.java. These Worker Threads were what initially processed enqueued Tasks (after popping
them off of QueueService), executing handlers directly. (In the initial stages of my project, QueueService
was the "source of truth", maintaining an in-memory Task map with the Worker-based pipeline via ExecutorService).
- NOTE: See "SpringQueue" (Base) for the original Worker Thread in action (which built off of GoQueue).

After the integration of ProcessingService.java, where database persistence was introduced, the Worker Thread
was demoted from being owners of business logic to just being "runners of units of work" that would pull
the latest persisted state from DataBase, "be acknowledged" and then ProcessingService would execute the
appropriate handler and save the result back to the DataBase. (They no longer manipulated retries or
re-enqueued failed tasks directly, basically just became vessels for invoking logic defined by ProcessingService).

As Redis was introduced (and further development of ProcessingService with optimistic locking, Redis locks, and so on),
eventually the purpose of the Worker Thread was phased out in conjunction with QueueService now having little more
purpose than being a wrapper for an ExecutorService that would submit Task IDs (first saved to the DataBase and later pulled
out and cached) for ProcessingService to process.

NOTE: There still technically are "worker threads" but they aren't defined in this way anymore (they lack
any real business logic and are just the default threads of QueueService's ExecutorService).
--------------------------------------------------------------------------------------------------
Kept as historical artifact for reference.
*/

@Deprecated
public class Worker implements Runnable {
    // Fields:
    private static final Logger logger = LoggerFactory.getLogger(Worker.class);
    private final Task task;
    private final TaskHandlerRegistry handlerRegistry;

    // Constructor:
    public Worker(Task task, TaskHandlerRegistry handlerRegistry) {
        this.task = task;
        this.handlerRegistry = handlerRegistry;
    }

    // "run" will basically be this project's version of GoQueue's StartWorker():
    @Override
    public void run() {
        String curThreadName = Thread.currentThread().getName();
        try {
            task.setAttempts(task.getAttempts() + 1);
            task.setStatus(TaskStatus.INPROGRESS);
            logger.info("[Worker {}] Processing task {} (Attempt {}, Type: {})", curThreadName, task.getId(), task.getAttempts(), task.getType());

            // Replacing the switch-case logic w/ this below:
            TaskHandler handler = handlerRegistry.getHandler(task.getType().name());
            handler.handle(task);
        } catch(Exception e) {
            logger.error("[Worker {}] Task {} failed due to error: {}", curThreadName, task.getId(), e.getMessage());
            task.setStatus(TaskStatus.FAILED);
        }
    }
}
