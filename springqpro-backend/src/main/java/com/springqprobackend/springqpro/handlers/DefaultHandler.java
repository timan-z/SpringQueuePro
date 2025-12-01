package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.config.TaskHandlerProperties;
import com.springqprobackend.springqpro.util.Sleeper;
import com.springqprobackend.springqpro.models.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("DEFAULT")
public class DefaultHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(DefaultHandler.class);
    private final Sleeper sleeper;
    private final TaskHandlerProperties props;

    public DefaultHandler(Sleeper sleeper, TaskHandlerProperties props) {
        this.sleeper = sleeper;
        this.props = props;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        logger.info("[Worker] No specific handler found for type '{}'. Executing default behavior.", task.getType());
        sleeper.sleep(props.getDefaultSleepTime());
        logger.info("Task {} (Type: {}) completed", task.getId(), task.getType());
    }
}
