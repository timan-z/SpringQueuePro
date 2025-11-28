package com.springqprobackend.springqpro.config;

import com.springqprobackend.springqpro.service.QueueService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessingMetricsConfig {
    @Bean
    public Counter tasksSubmittedManuallyCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_tasks_submitted_manually_total")
                .description("Total tasks submitted/created manually e.g., GraphQL queries (not including re-enqueued tasks).")
                .register(registry);
    }   // <-- NOTE:+TO-DO: I have not yet wired this one in... going to wait until implementing the React frontend (not sure where to place this for incrementation as of now).
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
                .publishPercentiles(0.50, 0.90, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }
    // The one below is for the number of tasks made by users sending GraphQL queries:
    @Bean
    public Counter apiTaskCreateCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_api_task_create_total")
                .description("Tasks created from GraphQL API")
                .register(registry);
    }   // TO-DO: GraphQL is my main API thing, but I'm keeping the REST stuff too -- maybe add another Counter for that specifically?
    @Bean
    public Counter queueEnqueueCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_queue_enqueue_total")
                .description("In-memory enqueue() calls (legacy path)")
                .register(registry);
    }   // <-- DEBUG: I can't remember if this is even used anymore at this point in the program...
    @Bean
    public Counter queueEnqueueByIdCounter(MeterRegistry registry) {
        return Counter.builder("springqpro_queue_enqueue_by_id_total")
                .description("enqueueById() calls feeding into ProcessingService")
                .register(registry);
    }
    @Bean
    public Gauge inMemoryQueueSizeGauge(MeterRegistry registry, QueueService queueService) {
        return Gauge.builder("springqpro_queue_memory_size", queueService, q -> q.getJobMapCount())
                .description("Number of tasks currently in legacy in-memory queue")
                .register(registry);
    }   // <-- DEBUG: I can't remember if this is even used anymore at this point in the program...
}
