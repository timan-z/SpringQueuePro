package com.springqprobackend.springqpro.service;

import com.springqprobackend.springqpro.config.QueueProperties;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import com.springqprobackend.springqpro.repository.TaskRepository;
import com.springqprobackend.springqpro.runtime.Worker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/* 2025-11-14-DEBUG:+REMINDER: After I finish my ProcessingService-based architectural overhaul of my program,
don't forget to remove QueueProperties from this file and adjust the second constructor to get rid of it
(and also adjust the UnitTests for QueueService because that's what the second constructor is for, and I'll need to tweak stuff).
^ Or maybe I can keep the manual declaration of ScheduledExecutor in the test constructor since the downside of
manual declaration is primarily for injection performance reasons, and I'm wiring it into the main constructor anyways?
- Also I don't think I ever shut down ScheduledExecutor...
- Now that I'm wiring in ScheduledExecutorService and ExecutorService, do I still need the .shutdown() statements?
*/
/* 2025-11-14-DEBUG:+REMINDER -- REFACTORED CHANGES TO QueueService.java:
- Method enqueue(Task task) is kept but refactored for ProcessingService.java now instead.
- Method retry(Task task, long delay) is basically obsolete. The Retry logic now lives in ProcessingService.java.
***********************************************************************************************************************
QueueService.java NEW PURPOSE (ProcessingService.java OVERHAUL) -- definitely port this stuff to README.md later:
--
I'm at the phase of my project now where I'm drastically overhauling and evolving the architecture of my project.
My program is really at the point now where it's transitioning away from a prototype queue (which is what GoQueue/SpringQueue really is)
mainly for testing asynchronous execution -> transforming it into an event-driven, persistence-aware processing pipeline.
[What QueueService was]:
- A Task Queue responsible for holding Tasks in-memory. (Tasks were held in memory).
- A Worker Pool manager that spins up Worker Threads via ExecutorService to process said Tasks concurrently.
- A Retry Scheduler that re-enqueues failed tasks after a delay (e.g., queue.retry(task, sleepTime).
It basically handled everything: persistence simulation, concurrency, retry, and orchestration.
That makes total sense for a PROTOTYPE (GoQueue/SpringQueue) where persistence wasn't in play yet (the Queue was the system's state).
Of course, this setup tightly coupled persistence and processing logic, meaning the DB couldn't easily "see" what was happening and couldn't
help enforce concurrency rules (see the comment I leave in TaskEntity.java or wherever where I touch on that).
[What QueueService is NOW / what I'm building to]:
Now I have,
- TaskEntity (the Persisted Task Record)
- TaskRepository (for Durability -- it's the system of Records)
- ProcessingService (for transactional, version-safe updates)
- the event flow (TaskCreatedEvent -> TaskCreatedListener -> ProcessingService)
I've basically changed the complete responsibility delegation.
MEANING
Now, QueueService is the layer that BRIDGES AND COORDINATES.
QueueService's new purpose is to be the in-memory bridge between the durable world (DataBase) and the
transient processing world (Worker Threads / Executors here).
QueueService no longer decides business outcomes (retry, fail, etc.) - instead it dispatches and coordinates.
-- ALSO:
QueueService's original enqueue() method where I submit new Worker(task,...) with the in-memory task is somewhat obsolete now.
I'm keeping it as legacy code for now, but now -- in accordance with how ProcessingService.java works -- I've added enqueueById()
which basically replaces it: submits a Runnable that calls ProcessingService.claimAndProcess() w/ a persisted Task ID,
"claims" the Task in DB, and processes it from there.
*/
@Service
public class QueueService {
    // Fields:
    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);
    private final ExecutorService executor;
    private final ConcurrentHashMap<String,Task> jobs;  // NOTE: W/ the ProcessingService.java overhaul, this field is obsolete along with probably some others.
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final TaskHandlerRegistry handlerRegistry;
    private final ScheduledExecutorService scheduler;   // NOTE: Handles high-level queue scheduling — e.g., when to enqueue, re-enqueue, or drain tasks.
    private final QueueProperties props;

    // DEBUG: 2025-11-13 EDIT: Additions below.
    private final TaskRepository taskRepository;        // DEBUG: For optional direct DB READS.
    private final ProcessingService processingService;  // DEBUG: Do processing via transactional service.

    // Constructor:
    @Autowired  // DEBUG: See if this fixes the issue!
    public QueueService(TaskHandlerRegistry handlerRegistry, TaskRepository taskRepository, ProcessingService processingService, @Qualifier("execService") ExecutorService executor, @Qualifier("schedExec") ScheduledExecutorService scheduler, QueueProperties props) {
        this.jobs = new ConcurrentHashMap<>();
        this.taskRepository = taskRepository;
        this.processingService = processingService;
        this.executor = executor;
        this.scheduler = scheduler;
        this.handlerRegistry = handlerRegistry;
        this.props = props;
    }

    // Constructor 2 (specifically for JUnit+Mockito testing purposes, maybe custom setups too I suppose):
    public QueueService(ExecutorService executor, TaskHandlerRegistry handlerRegistry, TaskRepository taskRepository, ProcessingService processingService, QueueProperties props){
        this.jobs = new ConcurrentHashMap<>();
        this.taskRepository = taskRepository;
        this.processingService = processingService;
        this.executor = executor;
        this.scheduler = Executors.newScheduledThreadPool(props.getSchedExecWorkerCount());
        this.handlerRegistry = handlerRegistry;
        this.props = props;
    }

    // DEBUG: 2025-11-13 EDIT: Method additions below. (Kind of replaces some but I'm going to keep my old legacy methods too).
    public void enqueueById(String id) {
        executor.submit(() -> processingService.claimAndProcess(id));
    }

    // Methods:
    // 1. Translating GoQueue's "func (q * Queue) Enqueue(t task.Task) {...}" function:
    // EDIT: THE VERSION OF enqueue BELOW IS NOW LEGACY CODE FROM THE PROTOTYPE PHASE! enqueueById ABOVE WILL BE USED BY ProcessingService.java.
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
    // TO-DO: DELETE THE METHOD BELOW WHEN ProcessingService.java-REHAUL IS COMPLETE AND TESTED!
    /*EDIT: LEGACY METHOD THAT WILL NO LONGER BE USED -- RETRY LOGIC WILL NOW LIVE IN ProcessingService.java
    (RETRY SCHEDULING IS PERSISTENCE-DRIVEN NOW, NOT MANUAL LIKE IT WAS WITH QueueService AND THE METHOD BELOW: */
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
    // NOTE: It's just hit me - I don't think I shutdown the scheduled executor????
    @PreDestroy
    public void shutdown() {
        logger.info("[Inside @PreDestroy method] Shutting down QueueService...");
        executor.shutdown();
        scheduler.shutdown();
        try {
            if(!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                scheduler.shutdownNow();
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            scheduler.shutdownNow();
        }
    }
}
