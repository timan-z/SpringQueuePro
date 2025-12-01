package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.config.TaskHandlerProperties;
import com.springqprobackend.springqpro.util.Sleeper;
import com.springqprobackend.springqpro.models.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("SMS")
public class SmsHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(SmsHandler.class);
    private final Sleeper sleeper;
    private final TaskHandlerProperties props;

    public SmsHandler(Sleeper sleeper, TaskHandlerProperties props) {
        this.sleeper = sleeper;
        this.props = props;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        sleeper.sleep(props.getSmsSleepTime());
        logger.info("Task {} (Type: {}) completed", task.getId(), task.getType());
    }
}
