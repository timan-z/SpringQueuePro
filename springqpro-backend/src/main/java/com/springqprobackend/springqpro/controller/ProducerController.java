package com.springqprobackend.springqpro.controller;

import com.springqprobackend.springqpro.service.QueueService;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
//@CrossOrigin(origins = "${CORS_ALLOWED_ORIGIN}") // for Netlify/Railway CORS (also local dev) <-- this line alone should replace the CORS stuff I had in Producer.go
public class ProducerController {
    private final QueueService queue;

    /* NOTE: Don't need @Autowired annotation here. Explicit @Autowired is only needed for multiple constructors or setter-based injection.
    -- Spring will automatically inject constructor parameters for single constructors.
    -- Regarding this.queue = queue;, see comment in Worker.java class (Java is pass-by-value for objects, but that value is the reference). */
    public ProducerController(QueueService queue) {
        this.queue = queue;
    }

    // 0. My GoQueue project had a struct "type EnqueueReq struct {...}" in its producer.go file. This would be the equivalent:
    // -- This is the DOT (Data Transfer Object).
    public static class EnqueueReq {
        public String payload;
        public TaskType type;
    }

    // 1. The equivalent of GoQueue's "http.HandleFunc("/api/enqueue", func(w http.ResponseWriter, r *http.Request) {...}" function:
    @PostMapping("/enqueue")
    public ResponseEntity<Map<String, String>> handleEnqueue(@RequestBody EnqueueReq req) {
        LocalDateTime createdAt = LocalDateTime.now();//.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Task t = new Task(
                "Task-" + System.nanoTime(),
                req.payload,
                req.type,
                TaskStatus.QUEUED,
                0,
                3,
                createdAt
        );
        queue.enqueue(t);
        return ResponseEntity.ok(Map.of("message", String.format("Job %s (Payload: %s, Type: %s) enqueued!", t.getId(), t.getPayload(), t.getType())));
    }

    // 2. The equivalent of GoQueue's "http.HandleFunc("/api/jobs", func(w http.ResponseWriter, r *http.Request) {...}" function:
    // From producer.go: "THIS IS FOR [GET /api/jobs] and [GET /api/jobs?status=queued]" <-- hence why we're using @RequestParam
    @GetMapping("/jobs")
    public ResponseEntity<List<Task>> handleListJobs(@RequestParam(required = false) String status) {
        Task[] allJobs = queue.getJobs();
        List<Task> filtered = Arrays.stream(allJobs).filter(t -> t != null && (status == null || t.getStatus().toString().equalsIgnoreCase(status))).collect(Collectors.toList());
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
                LocalDateTime.now()//.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
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

    // 6. This is for the [POST /api/clear]
    @PostMapping("/clear")
    public ResponseEntity<?> clearQueue() {
        queue.clear();
        return ResponseEntity.ok(Map.of("message", "All jobs in the queue cleared!"));
    }

}
