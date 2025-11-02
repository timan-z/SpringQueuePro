package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.service.QueueService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component("FAIL")
public class FailHandler implements TaskHandler {
    // Field
    private final QueueService queue;

    public FailHandler(@Lazy QueueService queue) {
        this.queue = queue;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        Random rando = new Random();
        double successChance = 0.25;
        if(rando.nextDouble() <= successChance) {
            Thread.sleep(2000);
            task.setStatus(TaskStatus.COMPLETED);
            System.out.printf("[Worker] Task %s (Type: fail - 0.25 success rate on retry) completed%n", task.getId());
        } else {
            task.setStatus(TaskStatus.FAILED);
            if (task.getAttempts() < task.getMaxRetries()) {
                System.out.printf("[Worker] Task %s (Type: fail - 0.25 success rate on retry) failed! Retrying...%n", task.getId());
                queue.retry(task, 1000);
                //queue.enqueue(t);
            } else {
                Thread.sleep(1000);
                System.out.printf("[Worker] Task %s (Type: fail - 0.25 success rate on retry) failed permanently!%n", task.getId());
            }
        }
    }
}
