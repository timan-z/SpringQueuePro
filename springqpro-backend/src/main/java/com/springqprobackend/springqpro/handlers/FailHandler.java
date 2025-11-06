package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component("FAIL")
public class FailHandler implements TaskHandler {
    // Field
    private static final Logger logger = LoggerFactory.getLogger(FailHandler.class);
    private final QueueService queue;
    private final Sleeper sleeper;
    private final Random random;

    @Autowired
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
            logger.info("[Worker] Task {} (Type: FAIL - 0.25 success rate on retry) completed", task.getId());
        } else {
            task.setStatus(TaskStatus.FAILED);
            if (task.getAttempts() < task.getMaxRetries()) {
                logger.warn("[Worker] Task {} (Type: FAIL - 0.25 success rate on retry) failed! Retrying...", task.getId());
                queue.retry(task, 1000);
            } else {
                sleeper.sleep(1000);
                logger.error("[Worker] Task {} (Type: FAIL - 0.25 success rate on retry) failed permanently!", task.getId());
            }
        }
    }
}
