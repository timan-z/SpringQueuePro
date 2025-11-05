package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;

import org.springframework.stereotype.Component;

@Component("TAKESLONG")
public class TakesLongHandler implements TaskHandler {
    private final Sleeper sleeper;

    public TakesLongHandler(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        sleeper.sleep(10000);
        task.setStatus(TaskStatus.COMPLETED);
        System.out.printf("[Worker] Task %s (Type: %s) completed%n", task.getId(), task.getType());
    }
}
