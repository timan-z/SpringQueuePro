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

/* NOTE(S)-TO-SELF - [PART TWO - 2025-11-13 EDITS]:
- The @Version annotation is a JPA annotation for "optimistic locking" -- a mechanism to prevent concurrent modifications
to the same entity in a database (this is for data integrity in multi-user environments).
- I'm working on Persistence Validation at the moment (making sure that changes to my in-memory Tasks via QueueService
e.g., Task's status goes from QUEUED -> INPROGRESS -> COMPLETED is persisted to the DataBase storage end (this is not yet implemented).
- You put @Version over a Long/Integer field to basically identify the version of the entity it's placed on.

[Explanation on why this is required / the race condition concern in my application wrt persisting data]:
In my project right now, I've got two separate layers of concurrency:
1. Application-level Concurrency (my QueueService in-memory stuff, what I started off with)
- My ExecutorService (worker threads) takes tasks off of the in-memory queue and processes them concurrently.
This part is well-contained and deterministic in memory. (Tasks are mapped to specific Workers for handling, logic is fine).
2. Persistence-level Concurrency (JPA, DB stuff, etc.)
- Basically, there's a possibility that these same threads could try to update and persist TaskEntity rows in the database
and those attempts might overlap in time (multiple threads/workers interacting with the same persistent record at once -- that
is a Persistence Race Condition).

^ Yes, it's not a particularly common concern; the way my system is set up, it's rare that something like this could happen.
My project design has it so that each Task is taken and "owned" (during processing) by one Worker Thread at a time (that's the whole dequeue system).
That makes it so that it's pretty unlikely there will be two threads will modify the same task simultaneously; ExecutorService takes care of most race conditions.
BUT --- IT CAN STILL HAPPEN (particularly externally with tools like Postman, GraphiQL, and so on).

Persistence race conditions can still happen in the event of:
- You have retries or resubmissions that cause overlapping updates for the same Task (identified by TaskID ofc).
- A user action via Postman, GraphiQL, and so on, changes the task as its being processed.
- I eventually add background database syncs or monitoring that touches tasks concurrently.

Handling this with @Version etc is the way that real Job Queue systems like SideKiq, Celery, and so on do it -- so I will too!!!
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

    // Optimistic locking field (protects against lost updates):
    @Version
    private Long version;

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
