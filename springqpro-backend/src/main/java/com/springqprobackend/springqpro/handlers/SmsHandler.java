package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("SMS")
public class SmsHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(SmsHandler.class);
    private final Sleeper sleeper;

    public SmsHandler(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        sleeper.sleep(1000);
        task.setStatus(TaskStatus.COMPLETED);
        logger.info("[Worker] Task {} (Type: {}) completed", task.getId(), task.getType());
    }
}
