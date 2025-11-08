package com.springqprobackend.springqpro.service;

import com.springqprobackend.springqpro.config.QueueProperties;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import com.springqprobackend.springqpro.runtime.Worker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/* NOTE: Initially did not have the @Service annotation because I was injecting this as a @Bean in SpringQueueApplication.java,
but as part of my ExecutorService Refactor, the @Bean has been removed so we should just have @Service here.
*/
@Service
public class QueueService {
    // Fields:
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);
    private final ExecutorService executor;
    private final ConcurrentHashMap<String,Task> jobs;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final TaskHandlerRegistry handlerRegistry;
    private final QueueProperties props;

    /* NOTE: In my Worker.java class, for each Job/Task-type handle function, I make the thread sleep for a certain amount of
    time (ms) e.g., Thread.sleep(2000); primarily to simulate different types of work (mimicking different processing times). But,
    they served a dual-purpose in preventing race conditions for re-enqueuing failed tasks. (I have both type "fail" and "fail-absolute"
    sleep for 1 second on known to-fail runs, for fails that do succeed -- I sleep for 2 seconds). But I guess the problem there is that
    it's only the Worker threads themselves running. So, there's a delay in the retry loop, but it only affects the thread itself instead
    of the Queue Service that actually does the enqueue functionality.
    -- This is a Recursive Enqueue Chain (each failed Worker enqueues its successor). For small recursive try counts, it's not a bit deal.
    But the issue arises in Scheduler Congestion and Tight Retry Loops under edge conditions.
    -- My Thread.sleep(...) statements rate limit my retries by processing time (making a natural backoff) so there is no immediate
    risk of runaway recursion. But my retry timing coupling is tied to processing time instead of Queue policy, so it'd be better
    to offload that retry scheduling into the Queue itself (good practice too because separation of concerns).
    -- Now, the Worker is only responsible for deciding WHETHER to retry.
    -- QueueService decides WHEN to retry.

    That's the reason for the field below. Important to get used to decoupled architecture. (Also see new method "public void retry(...){...}").
    NOTE: Going to keep my Thread.sleep(...); statements in Worker.java for now (also remember they served that first original purpose too).
    */
    private final ScheduledExecutorService scheduler;

    // Constructor:

    // NOTE: Defining worker.count in the application.properties file (switch to YAML? I have no idea).
    // DEBUG: Temp removing @Lazy from infront of TaskHandlerRegistry handlerRegistry (see if that's okay)
    @Autowired  // DEBUG: See if this fixes the issue!
    public QueueService(TaskHandlerRegistry handlerRegistry, QueueProperties props) {
        this.jobs = new ConcurrentHashMap<>();
        this.props = props;
        //this.lock = new ReentrantLock();
        this.executor = Executors.newFixedThreadPool(props.getMainExecWorkerCount(), r-> {
            Thread t = new Thread(r);
            t.setName("QS-Worker-" + t.getId());
            return t;
        });
        this.scheduler = Executors.newScheduledThreadPool(props.getSchedExecWorkerCount());
        this.handlerRegistry = handlerRegistry;
    }

    // Constructor 2 (specifically for JUnit+Mockito testing purposes, maybe custom setups too I suppose):
    // DEBUG: Temp removing @Lazy from infront of TaskHandlerRegistry handlerRegistry (see if that's okay)
    public QueueService(ExecutorService executor, TaskHandlerRegistry handlerRegistry, QueueProperties props){
        this.jobs = new ConcurrentHashMap<>();
        this.props = props;
        this.executor = executor;
        this.scheduler = Executors.newScheduledThreadPool(props.getSchedExecWorkerCount());
        this.handlerRegistry = handlerRegistry;
    }

    // Methods:
    // 1. Translating GoQueue's "func (q * Queue) Enqueue(t task.Task) {...}" function:
    public void enqueue(Task t) {
        t.setStatus(TaskStatus.QUEUED);
        writeLock.lock();
        try {
            jobs.put(t.getId(), t); // GoQueue: q.jobs[t.ID] = &t;
            executor.submit(new Worker(t, handlerRegistry));   // Submit an instance of a Worker to the ExecutorService (executor pool).
        } finally {
            writeLock.unlock();
        }
    }

    // 2. Translating GoQueue's "func (q * Queue) Clear() {...}" function:
    // (This is the method for "emptying the queue").
    public void clear() {
        writeLock.lock();
        try {
            jobs.clear();
        } finally {
            writeLock.unlock();
        }
    }

    // 3. Translating GoQueue's "func (q * Queue) GetJobs() []*task.Task {...}" function:
    // This is the method for returning a copy of all the Jobs (Tasks) we have:
    /*public Task[] getJobs() {
        readLock.lock();    // "read lock" only (in GoQueue: q.mu.RLock();)
        Task[] allTasks = new Task[jobs.size()];
        try {
            int i = 0;
            for(String key : jobs.keySet()) {
                allTasks[i] = jobs.get(key);
                i++;
            }
        } finally {
            readLock.unlock();
        }
        return allTasks;
    }*/
    public List<Task> getJobs() {
        readLock.lock();
        try {
            return new ArrayList<>(jobs.values());
        } finally {
            readLock.unlock();
        }
    }

    // 5. Translating GoQueue's "func (q * Queue) GetJobByID(id String) (*task.Task, bool)" function:
    // This is the method for returning a specific Job (Task) by ID:
    public Task getJobById(String id) {
        /* In my GoQueue version of this function, I returned a bool and the Task, but the reasons for that
        were entirely superfluous and I can just return null like a normal human being if Task isn't found. */
        readLock.lock();
        Task t = null;
        try {
            if(jobs.containsKey(id)) {
                t = jobs.get(id);
            }
        } finally {
            readLock.unlock();
        }
        return t;
    }

    // 6. Translating GoQueue's "func (q * Queue) DeleteJob(id string) bool" function:
    // This is the method for deleting a specific Job (Task) by ID:
    public boolean deleteJob(String id) {
        writeLock.lock();
        boolean res = false;
        try {
            if(jobs.containsKey(id)) {
                jobs.remove(id);
                res = true;
            }
        } finally {
            writeLock.unlock();
        }
        return res;
    }

    // 7. retry (see comment block above field "private final ScheduledExecutorService scheduler"):
    public void retry(Task t, long delayMs) {
        if(t.getStatus() != TaskStatus.FAILED) {
            logger.info("[QueueService] Retry request for Task {} (non-FAILED Task) rejected!", t.getId());
            return;
        }
        scheduler.schedule(() -> enqueue(t), delayMs, TimeUnit.MILLISECONDS);
    }

    // HELPER-METHOD(S): Might be helpful for monitoring endpoints if necessary...
    public int getJobMapCount() {
        return jobs.size();
    }

    /* NOTE-TO-SELF:
    - The ExecutorService needs explicit shutdown (else Spring Boot might hang on exit).
    - This ensures clean terminal (very important when the service is deployed on Railway or whatever).
    */
    @PreDestroy
    public void shutdown() {
        logger.info("[Inside @PreDestroy method] Shutting down QueueService...");
        executor.shutdown();
        try {
            if(!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
