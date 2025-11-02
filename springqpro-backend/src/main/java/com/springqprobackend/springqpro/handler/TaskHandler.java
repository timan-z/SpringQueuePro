package com.springqprobackend.springqpro.handler;

import com.springqprobackend.springqpro.models.Task;

public interface TaskHandler {
    void handle(Task task) throws InterruptedException;
}
