package com.springqprobackend.springqpro.service;

import com.springqprobackend.springqpro.config.QueueProperties;
import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.repository.TaskRepository;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/* NOTE(S)-TO-SELF:
- More on different assert methods: https://www.baeldung.com/junit-assertions
*/
// remember to scan back after and put any code that's mimicked across all tests in the @BeforeEach method!

/* Some other tests I can maybe write that aren't super high priority:
- Double enqueue prevention (code should reject duplicate IDs -- actually don't know if that's in the code).
- Max retry logic (task doesn’t re-enqueue beyond maxAttempts).
- Shutdown behavior (test service gracefully stops workers when needed).
- HandlerRegistry fallback — test that when no handler exists, the default handler runs.
*/

@ExtendWith(MockitoExtension.class)
public class QueueServiceTests {

    /* NOTE: Called DirectExecutorServiec because it runs commands immediately within the same thread
    (so I don't need to hassle with sleeps, race conditions, and all the concurrency-related headaches in testing). */
    private static class DirectExecutorService extends AbstractExecutorService {
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unity) { return true; }
        @Override
        public void execute(Runnable command) {
            /* Runs immediately in the calling thread. This is all that we're interested in using.
            The whole purpose of this nested private class definition is so we can run this function.
            (I'm using an ExecutorService in QueueService which is an extension of the Executor service.
            Ideally, I would have just done Executor ... = Runnable::run, but you can't do that
            w/ ExecutorService because it extends Executor and implements additional methods, so we have
            to implement them above (just the stubs) so we can run that. */
            /* NOTE: It's apparently best practice to inject an Executor for testing w/o sleeps or leniency stubs.
            (It's ideal to do that when testing systems where concurrency is involved, like this one). */
            command.run();  // runs immediately in the calling thread. This is all we're interested in
        }
    }

    @Mock
    private TaskHandlerRegistry handlerRegistry;
    @Mock
    private ExecutorService mockExecutor;
    @Mock
    private QueueProperties props;
    @Mock
    private TaskRepository taskRepo;
    @Mock
    private ProcessingService proService;
    @Mock
    private Counter queueEnqueueCounter;
    @Mock
    private Counter queueEnqueueByIdCounter;
    private QueueService queue;
    private Task t;

    /* NOTE: I can't do @InjectMocks over private QueueService queue; because the constructor arg list for my QueueService class
    looks like this: (@Value("${worker.count}") int workerCount, TaskHandlerRegistry handlerRegistry) and Mockito cannot autowire
    the constructor since it doesn't manage @Value("{worker.count}").
    -- There's probably a way to work around this, but for fast isolated unit tests, it's better to either do something like:
    "@BeforeEach
     void setUp() {
         MockitoAnnotations.openMocks(this); // Initializes all mocks (and related annotations) before each test.
         queue = new QueueService(2, handlerRegistry);
     }"
    The MockitoAnnotations line can be automated and omitted w/ @ExtendWith(MockitoExtension.class) */
    @BeforeEach
    void setUp() {
        ExecutorService immediateExecutor = new DirectExecutorService();
        //when(props.getMainExecWorkerCount()).thenReturn(5);
        //when(props.getSchedExecWorkerCount()).thenReturn(1);
        //queue = new QueueService(immediateExecutor, handlerRegistry, taskRepo, proService, props, queueEnqueueCounter, queueEnqueueByIdCounter);   // Manually constructing the queue (which is why there's no annotation above it earlier).
        // Init Task w/ no-args constructor:
        t = new Task();
        // id and type fields are the ubiquitous fields for testing (e.g., for identification):
        t.setId("Task-ArbitraryTestId");
        t.setType(TaskType.EMAIL);
    }

    @Disabled
    @Test
    void enqueue_shouldAddTask_toJobsMap() {
        // DEBUG:+NOTE: No type field causes error I'm pretty sure. Not sure about id though (maybe it should -- come back and look into this later).
        //queue.enqueue(t);
        /* EDIT: So it checks for FAILED before for a reason. We don't really care about what the Worker/Executor actually does, I just want to
        make sure that this Task gets enqueued into the pool and added to the jobs map (and so on). It will FAIL because I'm now relying on the
        direct executor that I define in the nested class. So even if I have a when...thenReturn(); call somewhere, it won't work. But that's okay
        because that's not really the concern of this Test. (I could just not use the direct executor if this were something I were actually concerned about). */
        //assertEquals(TaskStatus.FAILED, t.getStatus());
        //assertEquals(1, queue.getJobMapCount());    // Each test runs in isolation, so 1 job w/ only one task queued.
        //assertNotNull(queue.getJobById("Task-ArbitraryTestId"));
        /* NOTE: Tasks of Type EMAIL take ~2 seconds to finish execution (after which, their Type becomes COMPLETED).
        I could potentially make this thread sleep for 2-3 seconds and then check after to see if the type becomes COMPLETED.
        But I think it's supposed to be bad practice doing that in Unit Tests since they're meant to be very quick...
        */
    }

    @Disabled
    @Test
    void clear_shouldEmpty_theJobsMap() {
        //queue.enqueue(t);   // enqueue this job just so that the clear method can be invoked.

        // DEBUG: Not sure if this would work honestly. Get some clarity on if the execution of this test would be sequential (it should be right? Why am I questioning this?)
        //queue.clear();
        //assertEquals(0, queue.getJobMapCount());
        //assertNull(queue.getJobById("Task-ArbitraryTestId"), ()->"The value returned by getJobById() should be null post-clear()");
    }

    @Disabled
    @Test
    void jobsMap_canMap_manyJobs() {
        // t is declared in @BeforeEach method
        Task t2 = new Task();
        t2.setId("Task-ArbitraryTestId2");
        t.setType(TaskType.EMAIL);
        //queue.enqueue(t);
        //queue.enqueue(t2);
        //assertEquals(2, queue.getJobMapCount());
        //assertNotNull(queue.getJobById("Task-ArbitraryTestId"));
        //assertNotNull(queue.getJobById("Task-ArbitraryTestId2"));
    }

    @Disabled
    @Test
    void delete_shouldRemoveJob_fromJobsMap() {
        //queue.enqueue(t);

        //queue.deleteJob("Task-ArbitraryTestId");
        //assertEquals(0, queue.getJobMapCount());
        //assertNull(queue.getJobById("Task-ArbitraryTestId"), ()-> "The value returned by getJobById(\"id\") should be null after deleting the job identified by \"id\"");
    }

    @Disabled
    @Test
    void retry_shouldEnqueue_failedTask() throws InterruptedException {
        t.setStatus(TaskStatus.FAILED); // Append to t.
        /* NOTE: Originally had the when...thenReturn(...); function definition below in the @Test methods above too,
        but they would invoke unused stubbing errors when you'd attempt to run the tests. That's because the enqueue function
        is asynchronous (or rather async up until the task is enqueued into the executorService, but the test finishes almost
        immediately after that (the when... func never runs) -- and in the context of those tests, we don't care about what
        the instantiated Worker class does). */
        // NOTE: ^ Regardless, I still do get warnings in the console that seem to occur after each test completes...
        when(handlerRegistry.getHandler("EMAIL")).thenReturn(task -> {
            t.setStatus(TaskStatus.INPROGRESS); // EDIT: Was .COMPLETED, adjusted to .INPROGRESS.
        }); // <-- NOTE: (Cont) we have basically two delays below (which ensure that this func *will* be met).

        /* NOTE: retry method in QueueService.java is supposed to *not run* if the task you send in is NOT a FAILED status type.
        I think that might have been removed from my code when I refactored away from the switch-case method of handling different tasks.
        (I'm not 100% sure, come back to this and do some more digging - can probably be another unit test in this file). */
        //queue.retry(t, 10);
        TimeUnit.MILLISECONDS.sleep(20);
        /* In the context of this text below, I think we can just it check if the TaskStatus is COMPLETED (we're defining the
        handler behavior in the when...thenReturn(); method above anyways; I could have made it be QUEUED instead). */

        /* After originally having .COMPLETED in the when...thenReturn(); function above, I've picked up that the minor delays
        I enforce in this test delay it enough that the queued Worker reaches the stage where its Task Status is set to INPROGRESS.
        So, I may as well just also have the when...thenReturn(); function set the same (since we just want to make sure that it
        gets re-enqueued regardless of what the status is when we check). */
        //assertEquals(TaskStatus.INPROGRESS, queue.getJobById("Task-ArbitraryTestId").getStatus());
        //assertEquals(1, queue.getJobMapCount());
    }

    @Disabled
    @Test
    void retry_shouldReject_nonFailedTask() {
        t.setStatus(TaskStatus.COMPLETED);  // .retry(...) should reject tasks/jobs of status non-FAILED.
        //queue.retry(t, 10);
        // I think we can omit the TimeUnit...sleep and when...thenReturn because we're expecting auto-rejection.

        //assertNull(queue.getJobById("Task-ArbitraryTestId"));
        //assertEquals(0, queue.getJobMapCount());
    }

    /* NOTE: I also want to test for my ExecutorService field in QueueService is it properly shutting down.
    (Making sure that @PreDestroy, shutdown() methods etc correctly terminate the executor).
    But that's trickier since ExecutorService *is* an internal field. So I'll have to add that as a @Mock field here.
    Here's what we'd be testing directly:
    1. When shutdown() is called, it invokes executor.shutdown();
    2. If the executor doesn't terminate in time, it calls executor.shutdownNow();
    3. It handles InterruptedException correctly (interrupts the current thread and calls shutdownNow()). */
    // 1.
    /*@Test
    void shutdown_shouldTerminateExecutorService() throws InterruptedException {
        when(mockExecutor.awaitTermination(anyLong(), any())).thenReturn(true);
        QueueService queueService = new QueueService(mockExecutor, handlerRegistry, taskRepo, proService, props, queueEnqueueCounter, queueEnqueueByIdCounter);
        queueService.shutdown();    // voila.

        verify(mockExecutor).shutdown();
        verify(mockExecutor).awaitTermination(5, TimeUnit.SECONDS);
        verify(mockExecutor, never()).shutdownNow();
    }
    // 2.
    @Test
    void shutdown_shouldTerminate_ifTimeoutOccurs() throws InterruptedException {
        when(mockExecutor.awaitTermination(anyLong(), any())).thenReturn(false); // simulate timeout.
        QueueService service = new QueueService(mockExecutor, handlerRegistry, taskRepo, proService, props, queueEnqueueCounter, queueEnqueueByIdCounter);
        service.shutdown();

        verify(mockExecutor).shutdown();
        verify(mockExecutor).awaitTermination(5, TimeUnit.SECONDS);
        verify(mockExecutor).shutdownNow();  // force shutdown should trigger.
    }
    // 3.
    @Test
    void shutdown_shouldForceTerminate_ifInterrupted() throws InterruptedException {
        when(mockExecutor.awaitTermination(anyLong(), any())).thenThrow(new InterruptedException("simulated interruption"));

        QueueService service = new QueueService(mockExecutor, handlerRegistry, taskRepo, proService, props, queueEnqueueCounter, queueEnqueueByIdCounter);
        service.shutdown();

        verify(mockExecutor).shutdown();
        verify(mockExecutor).shutdownNow();
        // Verify that the thread was interrupted again
        assertTrue(Thread.interrupted(), "Current thread should be re-interrupted after catching InterruptedException");
    }*/

}