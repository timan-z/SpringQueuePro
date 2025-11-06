package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("TAKESLONG")
public class TakesLongHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(TakesLongHandler.class);
    private final Sleeper sleeper;

    public TakesLongHandler(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        sleeper.sleep(10000);
        task.setStatus(TaskStatus.COMPLETED);
        logger.info("[Worker] Task {} (Type: {}) completed", task.getId(), task.getType());
    }
}
