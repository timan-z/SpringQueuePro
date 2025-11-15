package com.springqprobackend.springqpro.runtime;

import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/* 2025-11-14-DEBUG:+REMINDER -- CHANGES TO Worker.java:
POST ProcessingService.java ARCHITECTURAL REFACTORING, MY QueueService Worker Threads STILL EXIST,
BUT THEY'VE BEEN DEMOTED FROM BEING OWNERS OF BUSINESS LOGIC TO JUST RUNNERS OF UNITS OF WORK!
QueueService is now using: "
public void enqueueById(String taskId) {
    executor.submit(() -> processingService.claimAndProcess(taskId));
}"
So what the Worker Threads do now is this:
- They pull the latest persisted state from the DataBase.
-- ProcessingService.java executes the corresponding handler.
-- It saves the result back to the DataBase.
Meaning that my Worker Threads are now execution vessels, no longer "decision makers."
They don’t manipulate retries or requeue directly — they just invoke the logic defined by ProcessingService.
- So, yeah, I definitely need to adjust a lot here.
*/
public class Worker implements Runnable {
    // Fields:
    private static final Logger logger = LoggerFactory.getLogger(Worker.class);
    private final Task task;
    /* NOTE-TO-SELF: Remember, Java is pass-by-value BUT for objects that value is the reference itself.
    "queue" will point to the same Queue instantiated elsewhere (references point to the same location in the memory heap). */

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
