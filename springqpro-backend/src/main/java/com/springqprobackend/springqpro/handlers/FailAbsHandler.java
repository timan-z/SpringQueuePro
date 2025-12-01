package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.config.TaskHandlerProperties;
import com.springqprobackend.springqpro.domain.exception.TaskProcessingException;
import com.springqprobackend.springqpro.util.Sleeper;
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
    private final TaskHandlerProperties props;

    public FailAbsHandler(@Lazy QueueService queue, Sleeper sleeper, TaskHandlerProperties props) {
        this.queue = queue;
        this.sleeper = sleeper;
        this.props = props;
    }

    @Override
    public void handle(Task task) throws InterruptedException, TaskProcessingException {
        // 2025-11-15-DEBUG: As of the ProcessingService.java-related architectural overhaul, Handlers no longer manually change state.
        sleeper.sleep(props.getFailAbsSleepTime());
        logger.warn("Task {} (Type: FAILABS - Fail-Absolute) failed! Retrying...", task.getId());
        throw new TaskProcessingException("Intentional fail for retry simulation");
    }
}
