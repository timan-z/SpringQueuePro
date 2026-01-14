# SpringQueuePro — Memory Optimization (2026-01-13)

This project is a massive memory hog and has been racking up my monthly Railway bill a ridiculous amount. This document is dedicated to a focused memory optimization pass performed on **2026-01-23** aimed at reducing JVM and application-level memory usage when running and hosting SpringQueuePro on any memory-billed PaaS platform. (As mentioned, I'm currently hosting this on Railway with their hobby plan which costs ~5CAD a month, which is the covered amount for your hosted project's memory usage. But this project, and even the base SpringQueue version, has been jumping my bill close to ~20CAD by month's end).

## Memory Usage Breakdown

It isn't *that* surprising that this project (**JVM + SpringBoot + metrics + Redis + GraphQL** being hosted **24/7**) has been so memory-hungry. The memory usage breakdown should look something like this:
| Source                              | Memory Hog (Why)        |
| ----------------------------------- | ------------------------------------------------ |
| **JVM default heap sizing**         | JVM happily reserves hundreds of MB even at idle |
| **Spring Boot auto-config**         | Loads frequently while not in use             |
| **ExecutorServices + schedulers**   | Threads = stack memory + queues                  |
| **Micrometer + metrics registries** | Counters, timers, tags accumulate                |
| **Redis + caches + in-memory maps** | authoritative + cached state            |
| **GraphQL + GraphiQL**              | Extra schema + reflection + servlet overhead     |

## Steps Taken to Optimize Memory Usage

### 1. Capping the JVM heap on Railway

Added the following environmental variable on Railway:
```
JAVA_TOOL_OPTIONS=-Xms64m -Xmx256m -XX:+UseG1GC -XX:MaxMetaspaceSize=128m
```
An explicit limit on the JVM's memory allocation w/ the heap and metaspace. This will prevent over-reservation and reducing idle memory use.

---

### 2. Disable GraphiQL in Production

Adding this to `application-prod.yml`:
```yaml
spring:
  graphql:
    graphiql:
      enabled: false
```
GraphiQL loads static assets, schema introspection, and additional servlet mappings that are unnecessary in production. Disabling it reduces baseline memory usage and class loading overhead.

---

### 3. Limiting Actuator & Micrometer Exposure
Original version of what's shown below included health, metrics, and prometheus in **include**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  metrics:
    enable:
      jvm: false
      process: false
      system: false
      executor: false
      hibernate: false
      logback: false
```
This disables high-cardinality, always-on metric groups (JVM, system, Hibernate, etc.) that consume memory continuously, while preserving the `/prometheus` endpoint needed for future Grafana integration.

---

### 4. Remove Legacy In-Memory Task Queue
I originally had these fields and methods marked as `@Deprecated` but unfortunately these still consume memory even if they're not in use.

- Removed:

  ```java
  private final ConcurrentHashMap<String, Task> jobs;
  ```
- Removed deprecated methods such as:

  ```java
  @Deprecated
  public List<Task> getJobs() {
      return new ArrayList<>(jobs.values());
  }
  ```

The legacy in-memory task map retained domain objects and enabled heap copying under load. Removing it eliminates unnecessary object retention and reinforces PostgreSQL as the single source of truth.

---

### 5. Cap Worker and Scheduler Threads via Configuration
Setting this explicit configuration in `application-prod.yml` for my `QueueProperties.java` file:
```yaml
queue:
  main-exec-worker-count: 5
  sched-exec-worker-count: 2
```
Thread stacks consume ~1MB each by default. Explicitly capping worker and scheduler counts prevents unbounded thread creation while still allowing meaningful concurrency for demos and testing.

---

### 6. Replace Executor Factories with Explicit ThreadPoolExecutor

Making this change in `ExecutorConfig.java` (*the original code is what's commented out in the snippet*):

```java
@Bean("execService")
public ExecutorService taskExecutor() {
    /*return Executors.newFixedThreadPool(props.getMainExecWorkerCount(), r -> {
            Thread t = new Thread(r);
            t.setName("QS-Worker-" + t.getId());
            return t;
    });*/
    return new ThreadPoolExecutor(
            props.getMainExecWorkerCount(),
            props.getMainExecWorkerCount(),
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r);
                t.setName("QS-Worker-" + t.getId());
                t.setDaemon(true);
                return t;
            }
    );
}
```

Using an explicit `ThreadPoolExecutor` avoids unbounded task queues, enables backpressure under load, and reduces memory spikes during stress testing.

---

### 7. Remove Legacy Metrics

Removed counters and gauges tied to deprecated code paths (e.g., legacy in-memory queue metrics). Got rid of stuff like (which relates to outdated, deprecated code):
```java
@Bean
    public Gauge inMemoryQueueSizeGauge(MeterRegistry registry, QueueService queueService) {
        return Gauge.builder("springqpro_queue_memory_size", queueService, q -> q.getJobMapCount())
                .description("Number of tasks currently in legacy in-memory queue")
                .register(registry);
}
```

Legacy metrics retained references to unused services and data structures, increasing object retention and complicating the runtime memory graph.
