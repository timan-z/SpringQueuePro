package com.springqprobackend.springqpro.controller.graphql;

import com.springqprobackend.springqpro.domain.entity.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.service.ProcessingService;
import com.springqprobackend.springqpro.service.TaskService;
import com.springqprobackend.springqpro.controller.controllerRecords.CreateTaskInput;
import com.springqprobackend.springqpro.controller.controllerRecords.UpdateTaskInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/* TaskGraphQLController.java
--------------------------------------------------------------------------------------------------
[HISTORY]:
GraphQL was introduced as the “modern API surface” for SpringQueuePro. REST worked fine during
the prototype phase, but GraphQL gave us:
  - typed schemas
  - explicit input types
  - streamlined task queries
  - future subscription support
This controller is where that GraphQL schema becomes executable. (Honestly, I've really just
wanted a reason to use GraphQL and get some experience working with it since it was a requirement
for a job position I was interviewed for but ultimately bombed).

[CURRENT ROLE]:
Implements GraphQL Query + Mutation resolvers for:
  - task(id)
  - tasks(status)
  - createTask(input)
  - updateTask(input)
  - deleteTask(id)
All operations flow through TaskService -> QueueService/ProcessingService -> PostgreSQL.

AUTH:
Each resolver enforces JWT authentication before performing any operation.

[FUTURE WORK]:
CloudQueue could add:
   • subscriptions for real-time task progress
   • federated schemas
   • dedicated analytics schema for metrics
--------------------------------------------------------------------------------------------------
*/

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
    public TaskGraphQLController(TaskService taskService) { this.taskService = taskService; }

    // QUERIES:
    @QueryMapping   // This is GraphQL query resolver.
    @PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public List<TaskEntity> tasks(@Argument TaskStatus status, Authentication auth) {
        String owner = auth.getName();
        logger.info("INFO: GraphQL 'tasks' (by status) Query sent by user:{}", owner);
        return taskService.getAllTasksForUser(status, owner);
    }
    @QueryMapping
    @PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public List<TaskEntity> tasksType(@Argument TaskType type, Authentication auth) {
        String owner = auth.getName();
        logger.info("INFO: GraphQL 'tasks' (by type) Query sent by user:{}", owner);
        return taskService.getAllTasksForUserByType(type, owner);
    }
    @QueryMapping
    @PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public TaskEntity task(@Argument String id, Authentication auth) {
        String owner = auth.getName();
        logger.info("INFO: GraphQL 'task' (by id:{}) Query sent by user:{}", id, owner);
        return taskService.getTaskForUser(id, owner).orElse(null);
    }

    // MUTATIONS:
    @MutationMapping
    //@PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public TaskEntity createTask(@Argument("input") CreateTaskInput input, Authentication auth) {
        String owner = auth.getName();
        logger.info("INFO: GraphQL 'createTask' Query sent by user:{}", owner);
        return taskService.createTaskForUser(input.payload(), input.type(), owner);
    }
    @MutationMapping
    @Transactional
    @PreAuthorize("isAuthenticated()")  // 2025-11-24-DEBUG: Securing my GraphQL resolvers for JWT.
    public TaskEntity updateTask(@Argument("input") UpdateTaskInput input, Authentication auth) {
        String owner = auth.getName();
        logger.info("INFO: GraphQL 'updateTask' (id={}) Query sent by user:{}", input.id(), owner);
        // Ownership check (User shouldn't be allowed to update other User's tasks; no cross-user updates):
        taskService.getTaskForUser(input.id(), owner).orElseThrow(() -> new RuntimeException("Task not found or not owned by current user."));
        taskService.updateStatus(input.id(), input.status(), input.attempts());
        return taskService.getTaskForUser(input.id(), owner).orElse(null);
    }
    @MutationMapping
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public boolean deleteTask(@Argument String id, Authentication auth) {
        String owner = auth.getName();
        logger.info("INFO: GraphQL 'deleteTask' (id={}) Query sent by user:{}", id, auth.getName());
        if(taskService.getTaskForUser(id, owner).isEmpty()) {
            return false;
        }
        return taskService.deleteTask(id);
    }




    // 2025-12-05-NOTE: Adding this one mostly for frontend purposes but it should be here too.
    /*@MutationMapping
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TaskEntity retryTask(@Argument String id, Authentication auth) {
        String owner = auth.getName();
        logger.info("INFO: GraphQL 'retryTask' (id={}) sent by user: {}", id, owner);
        // Ensure task belongs to current user
        TaskEntity task = taskService.getTaskForUser(id, owner).orElseThrow(() -> new RuntimeException("Task not found or not owned by user"));
        // Reset DB state: status = QUEUED, reset attempts to 0:
        taskService.updateStatus(id, TaskStatus.QUEUED, 0);
        // Re-fetch after DB update
        TaskEntity updated = taskService.getTaskForUser(id, owner).orElseThrow();

        processingService.enqueue(updated);

        logger.info("INFO: Task {} re-enqueued for retry", id);

        return updated;
    }*/



    /* 2025-12-04-NOTE: Adding a new method in the GraphQL Controller that exposes enums (useful for frontends
    so they can parse the acceptable enums defined in schema.graphqls and avoid hardcoding values). */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, List<String>> taskEnums() {
        return Map.of(
                "taskTypes", Arrays.stream(TaskType.values())
                        .map(Enum::name)
                        .toList(),
                "taskStatuses", Arrays.stream(TaskStatus.values())
                        .map(Enum::name)
                        .toList()
        );
    }

    // 2025-11-17-DEBUG: I can't remember why the thing below was added.
    /*@SchemaMapping(typeName = "Task", field = "payload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public String securePayload(TaskEntity entity) {
        return entity.getPayload();
    }*/
}
