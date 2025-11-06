package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("DEFAULT")
public class DefaultHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(DefaultHandler.class);
    private final Sleeper sleeper;

    public DefaultHandler(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        logger.info("[Worker] No specific handler found for type '{}'. Executing default behavior.", task.getType());
        //Thread.sleep(2000);
        sleeper.sleep(2000);
        task.setStatus(TaskStatus.COMPLETED);
        logger.info("[Worker] Task {} (Type: {}) completed", task.getId(), task.getType());
    }
}
