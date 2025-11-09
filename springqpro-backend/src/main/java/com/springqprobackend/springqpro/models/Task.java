package com.springqprobackend.springqpro.models;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;

import java.time.Instant;
import java.util.Objects;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;

/* NOTE:+2025-11-05-EDIT: I'm going to be adding Validation Annotations (Bean Validation) to this file.
The purpose is to basically guard against malformed input (primarily those received at Controller endpoints).
-- Controller endpoints would be marked with @Valid annotations on the @RequestBody (so stuff w/ NULL values, and so on, would be rejected ASAP).
-- My existing GlobalExceptionHandler can catch MethodArgumentNotValidException errors and return clean 400s with field-specific error messages.
IMPORTANT-NOTE: These validations won't auto-validate by themselves (like I can still create new Task(); without providing arguments and
everything would be fine. Validation happens when you explicitly call a Validator or if Spring MVC auto triggers validation with
like @Valid on a @RequestBody argument (the latter is more what I would be working with).
*/
// NOTE: ^ With these inclusions, GlobalExceptionHandler.java can be updated to handle Validation errors also (hence the "message"s).

@JsonInclude(JsonInclude.Include.NON_NULL)  // This annotation basically says no NULL fields allowed in JSON responses.
public class Task {
    // Fields:
    @NotBlank(message="Task ID cannot be blank.")
    private String id;

    @NotBlank(message="Payload cannot be blank.")   // DEBUG: At least for now? Maybe there's some that could? I don't know.
    @Size(max = 1000, message="Payload should not exceed 1000 characters.") // DEBUG: I think that's a good limit? Adjust later.
    private String payload;

    @NotNull(message="Task Type cannot be NULL.")
    private TaskType type;    // TO-DO:(?) I could change this to also be an enum like "status" (not sure how undefined/foreign request types are handled though).
    //@NotNull(message="Task Status cannot be NULL.") // <-- DEBUG: At least, I'm pretty sure. (WAIT NO -- Worker is meant to set this!!!)
    private TaskStatus status;

    @Min(value = 0, message="Attempts must be a non-negative integer value.")
    private int attempts;
    @Min(value = 0, message="Max retries must be a non-negative integer value.")
    private int maxRetries;

    @PastOrPresent(message="Creation Time Stamp cannot be in the future.")
    private Instant createdAt;   // TO-DO:(?) I format this with LocalDateTime. (I could change this to that type for better filtering and so on).

    // Constructor(s):
    // no-args Constructor - for potential frameworks like Jackson/JPA (that may serialize/deserialize the object):
    public Task() {}
    // Main Constructor:
    public Task(String id, String payload, TaskType type, TaskStatus status, int attempts, int maxRetries, Instant createdAt) {
        this.id = id;
        this.payload = payload;
        this.type = type;
        this.status = status;
        this.attempts = attempts;
        this.maxRetries = maxRetries;
        this.createdAt = createdAt;
    }

    // Getter methods:
    public String getId() {
        return id;
    }
    public String getPayload() {
        return payload;
    }
    public TaskType getType() {
        return type;
    }
    public TaskStatus getStatus() {
        return status;
    }
    public int getAttempts() {
        return attempts;
    }
    public int getMaxRetries() {
        return maxRetries;
    }
    public String getCreatedAt() {
        return createdAt.toString();
    }

    // Setter methods:
    public void setId(String id) {
        this.id = id;
    }
    public void setPayload(String payload) {
        this.payload = payload;
    }
    public void setType(TaskType type) {
        this.type = type;
    }
    public void setStatus(TaskStatus status) {
        this.status = status;
    }
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    // toString():
    @Override
    public String toString() {
        return "Task: [id: " + id + ", payload: " + payload + ", type: " + type + ", status: " + status + ", attempts: " + attempts + ", maxRetries: " + maxRetries + ", createdAt: " + createdAt + "]";
    }
    // hashCode():
    @Override
    public int hashCode() {
        // return Objects.hash(id, payload, type, status, attempts, maxRetries, createdAt);
        int hash = 7;
        hash = 31 * hash + (id == null ? 0: id.hashCode()); // NOTE: Not a numerical id! (stored in String for ar reason).
        hash = 31 * hash + (payload == null ? 0: payload.hashCode());
        hash = 31 * hash + (type == null ? 0: type.hashCode());
        hash = 31 * hash + (status == null ? 0: status.hashCode());
        hash = 31 * hash + attempts;
        hash = 31 * hash + maxRetries;
        hash = 31 * hash + (createdAt == null ? 0: createdAt.hashCode());
        return hash;
    }
    // equals():
    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(id, task.id) && Objects.equals(payload, task.payload) && Objects.equals(type, task.type) && status == task.status && attempts == task.attempts && maxRetries == task.maxRetries && Objects.equals(createdAt, task.createdAt);
    }
}
