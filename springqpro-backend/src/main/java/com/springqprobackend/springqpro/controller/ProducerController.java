package com.springqprobackend.springqpro.controller;

import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.service.QueueService;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
//@CrossOrigin(origins = "${CORS_ALLOWED_ORIGIN}") // for Netlify/Railway CORS (also local dev) <-- this line alone should replace the CORS stuff I had in Producer.go
public class ProducerController {
    private final QueueService queue;
    private final TaskService taskService;

    /* NOTE: Don't need @Autowired annotation here. Explicit @Autowired is only needed for multiple constructors or setter-based injection.
    -- Spring will automatically inject constructor parameters for single constructors.
    -- Regarding this.queue = queue;, see comment in Worker.java class (Java is pass-by-value for objects, but that value is the reference). */
    public ProducerController(QueueService queue, TaskService taskService) {
        this.queue = queue;
        this.taskService = taskService;
    }

    // 0. My GoQueue project had a struct "type EnqueueReq struct {...}" in its producer.go file. This would be the equivalent:
    // -- This is the DOT (Data Transfer Object).
    public static class EnqueueReq {
        @NotBlank(message="Payload cannot be blank.")
        @Size(max = 1000, message="Payload should not exceed 1000 characters.")
        public String payload;
        @NotNull(message="Task Type cannot be NULL.")
        public TaskType type;
    }

    // 1. The equivalent of GoQueue's "http.HandleFunc("/api/enqueue", func(w http.ResponseWriter, r *http.Request) {...}" function:
    /* TO-DO:+NOTE: Should probably also make one where a Task itself is directly instantiated since I added Validation Annotations to that file...
    -- I guess the problem is that the EnqueueReq one is already mapped to /enqueue? Or can I add another one for it?
    -- Not super high priority right now. Come back to this later and figure something out...
    * */
    @PostMapping("/enqueue")
    public ResponseEntity<Map<String, String>> handleEnqueue(@Valid @RequestBody EnqueueReq req) {
        if(req.type == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Task type is required."));
        }
        TaskEntity entity = taskService.createTask(req.payload, req.type);
        return ResponseEntity.ok(Map.of("message", String.format("Job %s (Payload: %s, Type: %s) enqueued! Status: %s", entity.getId(), entity.getPayload(), entity.getType().toString(), entity.getStatus().toString())));
    }

    // 2. The equivalent of GoQueue's "http.HandleFunc("/api/jobs", func(w http.ResponseWriter, r *http.Request) {...}" function:
    // From producer.go: "THIS IS FOR [GET /api/jobs] and [GET /api/jobs?status=queued]" <-- hence why we're using @RequestParam
    @GetMapping("/jobs")
    public ResponseEntity<List<Task>> handleListJobs(@RequestParam(required = false) String status) {
        //Task[] allJobs = queue.getJobs();
        //List<Task> filtered = Arrays.stream(allJobs).filter(t -> t != null && (status == null || t.getStatus().toString().equalsIgnoreCase(status))).collect(Collectors.toList());
        List<Task> allJobs = queue.getJobs();
        List<Task> filtered = allJobs.stream().filter(t -> t != null && (status == null || t.getStatus().toString().equalsIgnoreCase(status))).collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    // The handlers below will be for the individual methods in GoQueue's "http.HandleFunc("/api/jobs/", func(w http.ResponseWriter, r *http.Request) {...}" function:
    // 3. This is for [GET /api/jobs/:id]:
    @GetMapping("/jobs/{id}")
    public ResponseEntity<Task> handleGetJobById(@PathVariable String id) {
        Task t = queue.getJobById(id);
        if(t == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(t);
    }

    // 4. This is for [POST /api/jobs/:id/retry]:
    @PostMapping("/jobs/{id}/retry")
    public ResponseEntity<?> handleRetryJobById(@PathVariable String id) {
        Task t = queue.getJobById(id);
        if(t == null) return ResponseEntity.notFound().build();
        if(t.getStatus() != TaskStatus.FAILED) return ResponseEntity.badRequest().body(Map.of("error", "[Retry attempt] Can only retry failed jobs"));

        Task tClone = new Task(
                "Task-" + System.nanoTime(),
                t.getPayload(),
                t.getType(),
                TaskStatus.QUEUED,
                0,
                3,
                Instant.now()
                //LocalDateTime.now()//.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        queue.enqueue(tClone);
        return ResponseEntity.ok(tClone);
    }

    // 5. This is for [DELETE /api/jobs/:id]
    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<?> handleDeleteJobById(@PathVariable String id) {
        boolean deleteRes = queue.deleteJob(id);
        if(!deleteRes) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("message", String.format("Job %s deleted!", id)));
    }
    /* DEBUG:+NOTE:+TO-DO: ^ When I get to the stage where I start really expanding on the API endpoints (making this a deployable microservice),
    I want to change the return value here slightly. In best practice, it's not supposed to be a 200 (OK) response, RESTful API
    design has it so that what I'd do here is return 204 (No Content) sign, which would imply "the resource was deleted successfully,
    there is no further content to return."
    DEBUG:+NOTE:+TO-DO: Re-scan over all the functions, honestly, and evaluate if my return codes are correct later. (Do some more reading into return codes, etc).
    */

    // 6. This is for the [POST /api/clear]
    @PostMapping("/clear")
    public ResponseEntity<?> clearQueue() {
        queue.clear();
        return ResponseEntity.ok(Map.of("message", "All jobs in the queue cleared!"));
    }

}
