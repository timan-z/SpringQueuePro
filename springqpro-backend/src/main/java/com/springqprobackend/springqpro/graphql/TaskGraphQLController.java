package com.springqprobackend.springqpro.graphql;

import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.service.TaskService;
import org.springframework.stereotype.Controller;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import java.util.List;

/* NOTE(S)-TO-SELF:
- @QueryMapping is the GraphQL query resolver (to read operations / basically retrieve stuff, from what I understand).
- @MutationMapping is the GraphQL mutation resolver (to write operations / to modify data basically).
- @Argument maps GraphQL query args to method parameters.
- The schema defines how the client *sees* the data.
- Spring will automatically wire the /graphql endpoint (no controller mapping is needed).
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
    public List<TaskEntity> tasks(@Argument String status) {
        TaskStatus statusEnum = null;
        if(status != null) {
            try {
                statusEnum = TaskStatus.valueOf(status.toUpperCase());
            } catch(IllegalArgumentException e) {
                throw new RuntimeException("Invalid task status: " + status);
            }
        }
        return taskService.getAllTasks(statusEnum);
    }

    @QueryMapping
    public TaskEntity task(@Argument String id) {
        return taskService.getTask(id).orElse(null);
    }

    @MutationMapping    // GraphQL mutation resolver.
    public TaskEntity createTask(@Argument String payload, @Argument String type) {
        return taskService.createTask(payload, TaskType.valueOf(type.toUpperCase()));
    }
}
