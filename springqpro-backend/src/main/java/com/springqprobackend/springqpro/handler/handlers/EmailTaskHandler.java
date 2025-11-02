package com.springqprobackend.springqpro.handler.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.handler.TaskHandler;
import com.springqprobackend.springqpro.models.Task;

import org.springframework.stereotype.Component;

@Component("EMAIL")
public class EmailTaskHandler implements TaskHandler {
    @Override
    public void handle(Task task) throws InterruptedException {
        Thread.sleep(2000);
        task.setStatus(TaskStatus.COMPLETED);
        System.out.printf("[Worker] Task %s (Type: %s) completed%n", task.getId(), task.getType());
    }
}
