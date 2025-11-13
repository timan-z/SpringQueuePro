package com.springqprobackend.springqpro.listeners;

import com.springqprobackend.springqpro.events.TaskCreatedEvent;
import com.springqprobackend.springqpro.service.QueueService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TaskCreatedListener {
    // Field(s):
    private final QueueService queueService;
    // Constructor(s):
    public TaskCreatedListener(QueueService queueService) {
        this.queueService = queueService;
    }
    // Runs only after the creating transaction commits - prevents enqueu-before commit races:
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent ev) {
        queueService.enqueueById(ev.taskId());
    }
}
