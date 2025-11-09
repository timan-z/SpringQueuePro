package com.springqprobackend.springqpro.service;

import com.springqprobackend.springqpro.domain.*;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.repository.TaskRepository;
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
*/

@Service
public class TaskService {
    private final TaskRepository repository;
    private final QueueService queueService;
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
        Task task = new Task(
                entity.getId(),
                entity.getPayload(),
                entity.getType(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getMaxRetries(),
                entity.getCreatedAt()
        );
        queueService.enqueue(task);
        return entity;
    }

    public List<TaskEntity> getAllTasks(TaskStatus status) {
        if(status == null) return repository.findAll();
        return repository.findByStatus(status);
    }
    public Optional<TaskEntity> getTask(String id) {
        return repository.findById(id); // Optional basically means this value may or may not exist.
    }

    @Transactional
    public void updateStatus(String id, TaskStatus newStatus) {
        repository.findById(id).ifPresent(task -> {
            task.setStatus(newStatus);
            repository.save(task);
        });
    }
    @Transactional
    public boolean deleteTask(String id) {
        if(repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }
}
