package com.springqprobackend.springqpro.controller.rest;

import com.springqprobackend.springqpro.service.ProcessingService;
import com.springqprobackend.springqpro.service.QueueService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("/api/processing")
@PreAuthorize("isAuthenticated()")
public class ProcessingEventsController {
    // Field(s):
    private final ProcessingService processing;
    private final QueueService queueService;
    // Constructor(s):
    public ProcessingEventsController(ProcessingService processing, QueueService queueService) {
        this.processing = processing;
        this.queueService = queueService;
    }
    // Endpoints:
    @GetMapping("/events")
    public List<String> getEvents() {
        return processing.getRecentLogEvents();
    }

    @GetMapping("/workers")
    public Map<String, Integer> getWorkerStatus() {
        ThreadPoolExecutor exec = queueService.getExecutor();

        int active = exec.getActiveCount();
        int pool = exec.getPoolSize();
        int idle = pool - active;
        int queue = exec.getQueue().size();

        return Map.of(
                "active", active,
                "idle", idle,
                "inFlight", queue
        );
    }
}
