package com.springqprobackend.springqpro.graphql;

import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.service.TaskService;
import com.springqprobackend.springqpro.graphql.controllerRecords.CreateTaskInput;
import com.springqprobackend.springqpro.graphql.controllerRecords.UpdateTaskInput;
import org.springframework.stereotype.Controller;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/* NOTE(S)-TO-SELF:
- @QueryMapping is the GraphQL query resolver (to read operations / basically retrieve stuff, from what I understand).
- @MutationMapping is the GraphQL mutation resolver (to write operations / to modify data basically).
- @Argument maps GraphQL query args to method parameters.
- The schema defines how the client *sees* the data.
- Spring will automatically wire the /graphql endpoint (no controller mapping is needed).
*/

/* 2025-11-12-DEBUG:
DONE:
- task (query)
- tasks (query)
- createTask (mutation)
TO-DO FROM schema.graphqls:
- updateTask (mutation)
- deleteTask (mutation)
*/

@Controller // IMPORTANT NOTE: GraphQL controllers use @Controller, NOT @RestController (remember this!)
public class TaskGraphQLController {
    // Field(s):
    private final TaskService taskService;
    // Constructor(s):
    public TaskGraphQLController(TaskService taskService) {
        this.taskService = taskService;
    }

    @QueryMapping   // This is GraphQL query resolver.
    public List<TaskEntity> tasks(@Argument TaskStatus status) {
        return taskService.getAllTasks(status);
    }

    @QueryMapping
    public TaskEntity task(@Argument String id) {
        return taskService.getTask(id).orElse(null);
    }

    @MutationMapping
    public TaskEntity createTask(@Argument("input") CreateTaskInput input) {
        return taskService.createTask(input.payload(), input.type());
    }

    @MutationMapping
    @Transactional
    public TaskEntity updateTask(@Argument("input") UpdateTaskInput input) {
        taskService.updateStatus(input.id(), input.status(), input.attempts());
        return task(input.id());    // or "return taskService.getTask(id).orElse(null);"
    }

    @MutationMapping
    @Transactional
    public boolean deleteTask(@Argument String id) {
        return taskService.deleteTask(id);
    }
}
