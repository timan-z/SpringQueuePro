package com.springqprobackend.springqpro.controller;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;

// NOTE: I actually don't know if this is deprecated or not or if it's used in TaskRedisController.java...
/* controllerRecords.java
--------------------------------------------------------------------------------------------------
[HISTORY]:
As the project grew, many of the ad-hoc JSON payloads used in controllers needed structure.
Spring records became the canonical way to represent immutable request DTOs. Instead of scattering
small DTO classes across multiple files, these record definitions are collected here for easier navigation and code cleanliness.

[CURRENT ROLE]:
Provides strongly typed request models for:
  - Login / Register flows
  - Token refresh
  - REST-based Task operations
  - Misc GraphQL-ready helper types

[FUTURE WORK]:
These records may eventually be replaced by:
  - Contract-first schema generation (OpenAPI)
  - GraphQL input types exclusively
  - Codegen DTOs when CloudQueue adopts a gateway
--------------------------------------------------------------------------------------------------
*/

public record controllerRecords() {
    // public record types (mirroring the "input" types seen in my schema.graphqls):
    public record CreateTaskInput(String payload, TaskType type) {}
    public record UpdateTaskInput(String id, TaskStatus status, Integer attempts) {}
}
