package com.springqprobackend.springqpro.config;

import com.springqprobackend.springqpro.service.QueueService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ProcessingMetricsConfig {
    @Bean
    public Counter tasksSubmittedCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_tasks_submitted_total")
                .description("Total tasks submitted/created.")
                .register(registry);
    }
    @Bean
    public Counter tasksClaimedCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_tasks_claimed_total")
                .description("Total DB-backed tasks successfully claimed for processing.")
                .register(registry);
    }
    @Bean
    public Counter tasksCompletedCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_tasks_completed_total")
                .description("Total successfully completed tasks.")
                .register(registry);
    }
    @Bean
    public Counter tasksFailedCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_tasks_failed_total")
                .description("Total failed tasks")
                .register(registry);
    }
    @Bean
    public Counter tasksRetriedCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_tasks_retried_total")
                .description("Total scheduled retries")
                .register(registry);
    }
    @Bean
    public Timer processingTimer(MeterRegistry registry) {
        return Timer.builder("springqpro_task_processing_duration")
                .description("Time spent executing task handlers")
                .publishPercentiles(0.50, 0.90, 0.95)
                .publishPercentileHistogram()
                .sla(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(1))
                .register(registry);
    }
    // The one below is for the number of tasks made by users sending GraphQL queries:
    @Bean
    public Counter apiTaskCreateCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_api_task_create_total")
                .description("Tasks created from GraphQL API")
                .register(registry);
    }   // TO-DO: GraphQL is my main API thing, but I'm keeping the REST stuff too -- maybe add another Counter for that specifically?
    /*@Bean
    public Counter queueEnqueueCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_queue_enqueue_total")
                .description("In-memory enqueue() calls (legacy path)")
                .register(registry);
    }*/   // <-- DEBUG: I can't remember if this is even used anymore at this point in the program...
    // 2026-01-13-NOTE: ^ Removed because the jobs field is a memory hog even if it's deprecated and it's racking up my Railway bill.
    @Bean
    public Counter queueEnqueueByIdCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_queue_enqueue_by_id_total")
                .description("enqueueById() calls feeding into ProcessingService")
                .register(registry);
    }
    /*@Bean
    public Gauge inMemoryQueueSizeGauge(MeterRegistry registry, QueueService queueService) {
        return Gauge.builder("springqpro_queue_memory_size", queueService, q -> q.getJobMapCount())
                .description("Number of tasks currently in legacy in-memory queue")
                .register(registry);
    }*/   // <-- DEBUG: I can't remember if this is even used anymore at this point in the program...
    // 2026-01-13-NOTE: ^ Removed because the jobs field is a memory hog even if it's deprecated and it's racking up my Railway bill.
}
