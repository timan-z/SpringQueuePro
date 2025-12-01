package com.springqprobackend.springqpro.controller.rest;
/* NOTE: This file is primarily included for the purpose of practice, and also I guess "mirror testing."
This is just a REST Controller that one-to-one mimics the purpose of my GraphQL Controller.
(That's also why I have -- at least currently -- have it in the /graphql package directory).
*/

import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.service.TaskService;
import com.springqprobackend.springqpro.controller.controllerRecords.CreateTaskInput;
import com.springqprobackend.springqpro.controller.controllerRecords.UpdateTaskInput;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/* TaskRestController.java
--------------------------------------------------------------------------------------------------
[HISTORY]:
This controller was created as a REST mirror of the GraphQL controller for debugging,
educational purposes, and Postman-based testing. While GraphQL is the “official” API,
REST endpoints remain extremely useful when verifying behavior with simple clients.

[CURRENT ROLE]:
Provides REST-style CRUD endpoints for persisted Tasks. Mainly used for:
  - debugging
  - regression testing
  - side-by-side comparison with GraphQL
  - my own learning tbh
Production usage will happen through GraphQL.

[FUTURE WORK]:
In CloudQueue, REST may be removed or replaced with:
   • gateway-exposed read endpoints
   • admin-only REST management routes
--------------------------------------------------------------------------------------------------
*/

@RestController
@RequestMapping("/api/tasks")
public class TaskRestController {
    // Field(s):
    private TaskService taskService;
    // Constructor(s):
    public TaskRestController(TaskService taskService) { this.taskService = taskService; }

    @GetMapping("/jobs")
    public List<TaskEntity> tasks(@RequestParam(required = false) TaskStatus status) {
        return taskService.getAllTasks(status);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<TaskEntity> task(@PathVariable String id) {
        return taskService.getTask(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/create")
    public TaskEntity createTask(@RequestBody CreateTaskInput input) {
        return taskService.createTask(input.payload(), input.type());
    }

    @PatchMapping("/update/{id}")
    public ResponseEntity<TaskEntity> updateTask(@PathVariable String id, @RequestBody UpdateTaskInput input) {
        taskService.updateStatus(id, input.status(), input.attempts());
        return taskService.getTask(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        if (taskService.deleteTask(id)) return ResponseEntity.noContent().build();
        return ResponseEntity.notFound().build();
    }
}
