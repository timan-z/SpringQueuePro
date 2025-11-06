package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.service.QueueService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component("FAILABS")
public class FailAbsHandler implements TaskHandler {
    // Field
    private static final Logger logger = LoggerFactory.getLogger(FailAbsHandler.class);
    private final QueueService queue;
    private final Sleeper sleeper;

    public FailAbsHandler(@Lazy QueueService queue, Sleeper sleeper) {
        this.queue = queue;
        this.sleeper = sleeper;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        task.setStatus(TaskStatus.FAILED);
        if (task.getAttempts() < task.getMaxRetries()) {
            logger.info("[Worker] Task {} (Type: FAILABS - Fail-Absolute) failed! Retrying...", task.getId());
            queue.retry(task, 1000);
        } else {
            sleeper.sleep(1000);
            logger.info("[Worker] Task {} (Type: FAILABS - Fail-Absolute) failed permanently!", task.getId());
        }
    }
}
