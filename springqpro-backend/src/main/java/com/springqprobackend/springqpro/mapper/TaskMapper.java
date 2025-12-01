package com.springqprobackend.springqpro.mapper;

import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import com.springqprobackend.springqpro.models.Task;
import org.springframework.stereotype.Component;

import java.time.Instant;

/* TaskMapper.java
--------------------------------------------------------------------------------------------------
This file is responsible for converting between TaskEntity (DataBase persistent representation)
and Task (in-memory domain object that the Handlers consume and process). It's this file that
helps enforce domain consistency (Handlers operate strictly on domain objects, never on persistence-layer
entities). I originally just did the conversation in method with new Task object creation, but this logic
needed to be duplicated as ProcessingService came into the picture, so modularity was necessary.

[FUTURE WORK]:
CloudQueue might introduce additional DTOs for remote workers and built-in serialization for distributed execution.
--------------------------------------------------------------------------------------------------
*/

// 2025-11-30-NOTE: Preserving the comment below for now...
/* 2025-11-15-DEBUG:+NOTE(S)-TO-SELF:
This file is primarily to avoid doing TaskEntity -> Task conversion and so on within ProcessingService.java (decoupling purposes).
Remember, Handlers should be operating on Domain objects (Task) and not Persistence objects (TaskEntity).
This is just fundamental DDD principle: Repositories return Entities/Records and Business Logic consumes Domain Models.
-- Persistence concerns should NOT leak into Handlers! And so we have this file here. (Handlers should NOT take TaskEntity).
*/
@Component
public class TaskMapper {
    // TaskEntity -> Task conversion method. (Convert persistence-layer TaskEntity into a pure domain Task model).
    public Task toDomain(TaskEntity e) {
        if(e == null) return null;
        return new Task(
                e.getId(),
                e.getPayload(),
                e.getType(),
                e.getStatus(),
                e.getAttempts(),
                e.getMaxRetries(),
                e.getCreatedAt(),
                e.getCreatedBy()
        );
    }
    // Reconciles Domain (Task) -> TaskEntity after handler completes.
    public void updateEntity(Task domain, TaskEntity entity) {
        if(domain == null | entity == null) return;
        entity.setPayload(domain.getPayload());
        entity.setType(domain.getType());
        entity.setStatus(domain.getStatus());
        entity.setAttempts(domain.getAttempts());
        entity.setMaxRetries(domain.getMaxRetries());
        entity.setCreatedAt(Instant.parse(domain.getCreatedAt()));
        entity.setCreatedBy(domain.getCreatedBy());
    }
}
