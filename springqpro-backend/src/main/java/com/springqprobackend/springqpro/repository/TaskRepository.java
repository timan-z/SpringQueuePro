package com.springqprobackend.springqpro.repository;

import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/* TaskRepository.java
--------------------------------------------------------------------------------------------------
This is the JPA (Java Persistence API) repository for TaskEntity.
- Since it extends JpaRepository, it'll have all the basic CRUD operations for manipulating tasks,
but I've also got custom methods for Atomic state transition (extra locking defense in addition to
Redis optimistic locking) and, in addition, querying by status.
- This file is essentially the persistence backbone of the Task Orchestration System (very important).

[HISTORY]:
This file was added once the system shifted away from a fully in-memory queue to persistence-based.
--------------------------------------------------------------------------------------------------
*/

/*
NOTES-TO-SELF:
- Spring Data JPA can automatically create repositories (DAOs - Data Access Objects) from interfaces.
- By extending JpaRepository<TaskEntity, String>, you instantly get CRUD methods like:
-- findAll()
-- findById(String id)
-- save(TaskEntity entity)
-- deleteById(String id)
You can also define query methods using naming conventions.
--------------------------------------------------------------------------------------------------------------
NOTE(s)-TO-SELF - [PART TWO - 2025-11-13 EDITS]:
- The @Modifying annotation in Spring Data JPA is for indicating a repository method (annotated with @Query)
performs a data modification (CRUD) operation.
- The @Transactional annotation in Spring Boot is used to manage transactions declaratively. I use it
in service/TaskService.java almost as a sort of "rollback" mechanism, but it also has ACID properties
(Atomicity, Consistency, Isolation, and Durability) for database operations (which is important here).
*/

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {
    // NOTE: ^ In JpaRepository<TaskEntity, String>, the generic parameters mean entity type and primary key type.

    /* Expanding on "You can also define query methods using naming conventions."
    Below is an example of that. W/ these, you never write SQL manually for simple queries.
    -- Method names like findByStatus are descriptive enough to be parsed by Spring to generate queries.
    -- Spring would read the line below as "SELECT * FROM tasks WHERE status = ?" (REMEMBER THIS!)
    */
    List<TaskEntity> findByStatus(TaskStatus status);
    List<TaskEntity> findByType(TaskType type); // <-- 2025-11-19-DEBUG: I don't know why I'm only adding this now.

    // 2025-11-25-NOTE: IMPLEMENTING JWT OWNERSHIP ADDITION:
    @Query("""
            SELECT t
            FROM TaskEntity t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:type IS NULL OR t.type = :type)
            """)
    List<TaskEntity> findByStatusAndType(@Param("status") TaskStatus status, @Param("type") TaskType type);

    /*
    worker tries to claim e.g., via transitionStatus(id, QUEUED, INPROGRESS, currentAttempts+1).
    If it returns 1, the claim succeeded; otherwise another worker claimed it or the status changed.
    (This is meant to return the number of rows affected, so !=1 means the update didn't work for xyz reason.
    Transition status only if the current status matches "from").
    This is the atomic SQL-style step that prevents two workers from both starting work on the same task.
    */
    @Modifying
    @Transactional
    @Query("UPDATE TaskEntity t SET t.status = :to, t.attempts = :attempts WHERE t.id = :id AND t.status = :from")
    int transitionStatus(@Param("id") String id, @Param("from") TaskStatus from, @Param("to") TaskStatus to, @Param("attempts") int attempts);

    // 2025-11-17-DEBUG: ADDING ANOTHER ONE TO SET A RE-ENQUEUED TASK'S STATUS BACK TO QUEUED!!!
    @Modifying
    @Transactional
    @Query("UPDATE TaskEntity t SET t.status = :to WHERE t.id = :id AND t.status = :from")
    int transitionStatusSimple(@Param("id") String id, @Param("from") TaskStatus from, @Param("to") TaskStatus to);

    // 2025-11-25-DEBUG: NEW METHODS BELOW HELP ENFORCE JWT USER OWNERSHIP OF TASKS!!! (IN THE SERVICE/CONTROLLER LAYER).
    List<TaskEntity> findAllByCreatedBy(String createdBy);
    List<TaskEntity> findByStatusAndCreatedBy(TaskStatus status, String createdBy);
    List<TaskEntity> findByTypeAndCreatedBy(TaskType type, String createdBy);
    Optional<TaskEntity> findByIdAndCreatedBy(String id, String createdBy);
}
