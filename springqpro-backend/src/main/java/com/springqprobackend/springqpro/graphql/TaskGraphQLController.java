package com.springqprobackend.springqpro.graphql;

import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.service.ProcessingService;
import com.springqprobackend.springqpro.service.TaskService;
import com.springqprobackend.springqpro.graphql.controllerRecords.CreateTaskInput;
import com.springqprobackend.springqpro.graphql.controllerRecords.UpdateTaskInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@Controller // IMPORTANT NOTE: GraphQL controllers use @Controller, NOT @RestController (remember this!)
public class TaskGraphQLController {
    // Field(s):
    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);
    private final TaskService taskService;
    // Constructor(s):
    public TaskGraphQLController(TaskService taskService) {
        this.taskService = taskService;
    }

    @QueryMapping   // This is GraphQL query resolver.
    @PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public List<TaskEntity> tasks(@Argument TaskStatus status, Authentication auth) {
        logger.info("INFO: GraphQL tasks (by status) Query sent by user:{}", auth.getName());
        return taskService.getAllTasks(status);
    }
    @QueryMapping
    @PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public List<TaskEntity> tasksType(@Argument TaskType type, Authentication auth) {
        logger.info("INFO: GraphQL tasks (by type) Query sent by user:{}", auth.getName());
        return taskService.getAllTasks(type);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public TaskEntity task(@Argument String id, Authentication auth) {
        logger.info("INFO: GraphQL task (by id) Query sent by user:{}", auth.getName());
        return taskService.getTask(id).orElse(null);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public TaskEntity createTask(@Argument("input") CreateTaskInput input, Authentication auth) {
        logger.info("INFO: GraphQL createTask Query sent by user:{}", auth.getName());
        return taskService.createTask(input.payload(), input.type());
    }

    @MutationMapping
    @Transactional
    @PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public TaskEntity updateTask(@Argument("input") UpdateTaskInput input, Authentication auth) {
        logger.info("INFO: GraphQL updateTask Query sent by user:{}", auth.getName());
        taskService.updateStatus(input.id(), input.status(), input.attempts());
        return taskService.getTask(input.id()).orElse(null);
        //return task(input.id())  ;    // or "return taskService.getTask(id).orElse(null);"
    }

    @MutationMapping
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public boolean deleteTask(@Argument String id, Authentication auth) {
        logger.info("INFO: GraphQL deleteTask Query sent by user:{}", auth.getName());
        return taskService.deleteTask(id);
    }

    @SchemaMapping(typeName = "Task", field = "payload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public String securePayload(TaskEntity entity) {
        return entity.getPayload();
    }
}
