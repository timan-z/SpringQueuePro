package com.springqprobackend.springqpro.domain;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import jakarta.persistence.*;

import java.time.Instant;

/* NOTE(S)-TO-SELF:
- @Entity marks this class for persistence (Hibernate will map it to a DataBase table). Each field corresponds to a column.
- ^ will assume same table name, but you can specify with @Table(name="tasks") <-- good practice for table names is all lowercase.
- @Id obv defines the primary key.
- @Enumrated stores enum names (like "QUEUED") instead of integers.
- @Column controls SQL column behavior (type, name).
- Var "Instant" is preferred for timestamps over "LocalDateTime" (UTC-Friendly).
Spring Boot w/ Spring Data JPA, through Hibernate, automatically generates the SQL schema and performs CRUD operations.

Also, this file isn't meant to re-place Task.java (which stands as the runtime / in-memory representation of a Task).
This TaskEntity file here is the persistence representation (database-backed model for durable storage).
*/

@Entity
@Table(name="tasks")
public class TaskEntity {
    @Id
    private String id;

    @Column(columnDefinition="text")    // Allows long payloads
    private String payload;

    @Enumerated(EnumType.STRING)    // Store enum as readable string (as opposed to numeric).
    private TaskType type;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private int attempts;
    private int maxRetries;

    @Column(name="created_at")  // custom DataBase column name.
    private Instant createdAt;

    // NOTE: Hibernate needs a no-args constructor. Think about using "protected" for all of these in the future tbh.
    protected TaskEntity() {}
    // Constructor:
    public TaskEntity(String id, String payload, TaskType type, TaskStatus status, int attempts, int maxRetries, Instant createdAt) {
        this.id = id;
        this.payload = payload;
        this.type = type;
        this.status = status;
        this.attempts = attempts;
        this.maxRetries = maxRetries;
        this.createdAt = createdAt;
    }

    // JPA will use the getter and setter methods to map to the table columns:
    // getters:
    public String getId() { return id; }
    public String getPayload() { return payload; }
    public TaskType getType() { return type; }
    public TaskStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public int getMaxRetries() { return maxRetries; }
    public Instant getCreatedAt() { return createdAt; }
    // setters:
    public void setId(String id) { this.id = id; }
    public void setPayload(String payload) { this.payload = payload; }
    public void setType(TaskType type) { this.type = type; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
