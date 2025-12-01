package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.config.TaskHandlerProperties;
import com.springqprobackend.springqpro.domain.exception.TaskProcessingException;
import com.springqprobackend.springqpro.util.Sleeper;
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
    private final TaskHandlerProperties props;

    @Autowired
    public FailHandler(@Lazy QueueService queue, Sleeper sleeper, TaskHandlerProperties props) {
        this.queue = queue;
        this.sleeper = sleeper;
        this.random = new Random(); // This is the constructor called by Worker.java, it just uses default Random init.
        this.props = props;
    }

    // This is the for-testing constructor where random is provided by user (to control testing outcomes, see: FailHandlerTests.java):
    public FailHandler(@Lazy QueueService queue, Sleeper sleeper, Random random, TaskHandlerProperties props) {
        this.queue = queue;
        this.sleeper = sleeper;
        this.random = random;
        this.props = props;
    }

    @Override
    public void handle(Task task) throws InterruptedException, TaskProcessingException {
        double successChance = 0.25;
        if(random.nextDouble() <= successChance) {
            sleeper.sleep(props.getFailSuccSleepTime());
            logger.info("Task {} (Type: FAIL - 0.25 success rate on retry) completed", task.getId());
        } else {
            // 2025-11-15-DEBUG: As of the ProcessingService.java-related architectural overhaul, Handlers no longer manually change state.
            sleeper.sleep(props.getFailSleepTime());
            logger.warn("Task {} (Type: FAIL - 0.25 success rate on retry) failed! Retrying...", task.getId());
            throw new TaskProcessingException("Intentional fail for retry simulation");
        }
    }
}
