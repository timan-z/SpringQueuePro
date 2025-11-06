package com.springqprobackend.springqpro.runtime;

import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        try {
            task.setAttempts(task.getAttempts() + 1);
            task.setStatus(TaskStatus.INPROGRESS);
            logger.info("[Worker] Processing task {} (Attempt {}, Type: {})", task.getId(), task.getAttempts(), task.getType());

            // Replacing the switch-case logic w/ this below:
            TaskHandler handler = handlerRegistry.getHandler(task.getType().name());
            handler.handle(task);
        } catch(Exception e) {
            logger.error("[Worker] Task {} failed due to error: {}", task.getId(), e.getMessage());
            task.setStatus(TaskStatus.FAILED);
        }
    }
}
