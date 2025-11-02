package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import org.springframework.stereotype.Component;

@Component("DEFAULT")
public class DefaultHandler implements TaskHandler {
    @Override
    public void handle(Task task) throws InterruptedException {
        System.err.printf("[Worker] No specific handler found for type '%s'. Executing default behavior.%n", task.getType());
        Thread.sleep(2000);
        task.setStatus(TaskStatus.COMPLETED);
        System.out.printf("[Worker] Task %s (Type: %s) completed%n", task.getId(), task.getType());
    }
}
