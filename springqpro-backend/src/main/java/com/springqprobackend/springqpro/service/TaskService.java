package com.springqprobackend.springqpro.service;

import com.springqprobackend.springqpro.domain.*;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.events.TaskCreatedEvent;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/* NOTES-TO-SELF:
- This a service layer for calling repositories (DataBase) or queues.
- This layer is called by the controller.
- This file combines persistence (DataBase) and in-memory processing (QueueService).
^ In other words: This Service orchestrates persistence and queue interaction.
-- @Transactional is an annotation for if anything fails inside the method, then DataBase changes will roll back.
EDIT: Forgot to wire this whole part in my bad.
- ApplicationEventPublisher is an interface in the Spring Framework that encapsulates event publication functionality,
allowing for decoupling of application components through event-driven architecture. [Google AI Description].
*/

@Service
public class TaskService {
    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);
    private final TaskRepository repository;
    private final QueueService queueService;

    @Autowired
    private ApplicationEventPublisher publisher;

    public TaskService(TaskRepository repository, QueueService queueService) {
        this.repository = repository;
        this.queueService = queueService;
    }

    @Transactional
    public TaskEntity createTask(String payload, TaskType type) {
        TaskEntity entity = new TaskEntity(
                "Task-" + System.nanoTime(),
                payload,
                type,
                TaskStatus.QUEUED,
                0,
                3,
                Instant.now()
        );
        repository.save(entity);
        logger.info("[TaskService] saved task {}, publishing event", entity.getId());
        publisher.publishEvent(new TaskCreatedEvent(this, entity.getId()));
        logger.info("[TaskService] published TaskCreatedEvent for {}", entity.getId());
        return entity;
    }

    public List<TaskEntity> getAllTasks(TaskStatus status) {
        if (status == null) return repository.findAll();
        return repository.findByStatus(status);
    }
    /* 2025-11-19-NOTE: I don't know why I didn't add this before, but I should definitely have the option
    to getAllTasks via TaskType type as well, so I'm going to overload the method above. */
    public List<TaskEntity> getAllTasks(TaskType type) {
        if(type == null) return repository.findAll();
        return repository.findByType(type);
    }

    public Optional<TaskEntity> getTask(String id) {
        return repository.findById(id); // Optional basically means this value may or may not exist.
    }

    @Transactional
    public void updateStatus(String id, TaskStatus newStatus, int updAttempts) {
        repository.findById(id).ifPresent(task -> {
            task.setStatus(newStatus);
            task.setAttempts(updAttempts);
            repository.save(task);
        });
    }

    @Transactional
    public boolean deleteTask(String id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }
}
