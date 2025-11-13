package com.springqprobackend.springqpro.graphql;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;

public record controllerRecords() {
    // public record types (mirroring the "input" types seen in my schema.graphqls):
    public record CreateTaskInput(String payload, TaskType type) {}
    public record UpdateTaskInput(String id, TaskStatus status, Integer attempts) {}
}
