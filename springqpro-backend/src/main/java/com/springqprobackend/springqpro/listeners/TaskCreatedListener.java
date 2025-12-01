package com.springqprobackend.springqpro.listeners;

import com.springqprobackend.springqpro.domain.event.TaskCreatedEvent;
import com.springqprobackend.springqpro.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TaskCreatedListener {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(TaskCreatedListener.class);
    private final QueueService queueService;
    // Constructor(s):
    public TaskCreatedListener(QueueService queueService) {
        this.queueService = queueService;
    }
    // Runs only after the creating transaction commits - prevents enqueu-before commit races:
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent ev) {
        logger.info("[TaskCreatedListener] received event for {}", ev.taskId());
        queueService.enqueueById(ev.taskId());
        logger.info("[TaskCreatedListener] enqueuedById {}", ev.taskId());
    }
}
