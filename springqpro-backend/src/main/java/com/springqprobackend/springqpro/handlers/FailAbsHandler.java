package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.service.QueueService;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component("FAILABS")
public class FailAbsHandler implements TaskHandler {
    // Field
    private final QueueService queue;

    public FailAbsHandler(@Lazy QueueService queue) {
        this.queue = queue;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        task.setStatus(TaskStatus.FAILED);
        if (task.getAttempts() < task.getMaxRetries()) {
            System.out.printf("[Worker] Task %s (Type: fail-absolute) failed! Retrying...%n", task.getId());
            queue.retry(task, 1000);
        } else {
            Thread.sleep(1000);
            System.out.printf("[Worker] Task %s (Type: fail-absolute) failed permanently!%n", task.getId());
        }
    }
}
