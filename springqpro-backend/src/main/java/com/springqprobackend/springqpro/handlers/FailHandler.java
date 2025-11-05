package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
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
    private final Sleeper sleeper;
    private final Random random;

    public FailHandler(@Lazy QueueService queue, Sleeper sleeper) {
        this.queue = queue;
        this.sleeper = sleeper;
        this.random = new Random(); // This is the constructor called by Worker.java, it just uses default Random init.
    }

    // This is the for-testing constructor where random is provided by user (to control testing outcomes, see: FailHandlerTests.java):
    public FailHandler(@Lazy QueueService queue, Sleeper sleeper, Random random) {
        this.queue = queue;
        this.sleeper = sleeper;
        this.random = random;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        double successChance = 0.25;
        if(random.nextDouble() <= successChance) {
            sleeper.sleep(2000);
            task.setStatus(TaskStatus.COMPLETED);
            System.out.printf("[Worker] Task %s (Type: fail - 0.25 success rate on retry) completed%n", task.getId());
        } else {
            task.setStatus(TaskStatus.FAILED);
            if (task.getAttempts() < task.getMaxRetries()) {
                System.out.printf("[Worker] Task %s (Type: fail - 0.25 success rate on retry) failed! Retrying...%n", task.getId());
                queue.retry(task, 1000);
            } else {
                sleeper.sleep(1000);
                System.out.printf("[Worker] Task %s (Type: fail - 0.25 success rate on retry) failed permanently!%n", task.getId());
            }
        }
    }
}
