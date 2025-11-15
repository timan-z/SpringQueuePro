package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.config.TaskHandlerProperties;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;

import com.springqprobackend.springqpro.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("DATACLEANUP")
public class DataCleanUpHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(DataCleanUpHandler.class);
    private final Sleeper sleeper;
    private final TaskHandlerProperties props;

    public DataCleanUpHandler(Sleeper sleeper, TaskHandlerProperties props) {
        this.sleeper = sleeper;
        this.props = props;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        sleeper.sleep(props.getDataCleanUpSleepTime());
        logger.info("Task {} (Type: {}) completed", task.getId(), task.getType());
    }
}
