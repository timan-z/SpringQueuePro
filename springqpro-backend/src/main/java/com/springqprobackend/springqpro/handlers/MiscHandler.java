package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;

/* Think I can map unidentifiable jobs to this one? Actually not 100% sure how I should go about handling
unidentified task/job types

*/
public class MiscHandler implements TaskHandler {
    @Override
    public void handle(Task task) throws InterruptedException {
        Thread.sleep(2000);
        task.setStatus(TaskStatus.COMPLETED);
        System.out.printf("[Worker] Task %s (Type: %s) completed%n", task.getId(), task.getType());
    }
}
