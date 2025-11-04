package com.springqprobackend.springqpro.service;

import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.enums.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/* NOTE(S)-TO-SELF:
- More on different assert methods: https://www.baeldung.com/junit-assertions
*/
// remember to scan back after and put any code that's mimicked across all tests in the @BeforeEach method!

@ExtendWith(MockitoExtension.class)
public class QueueServiceTests {
    @Mock
    private TaskHandlerRegistry handlerRegistry;

    private QueueService queue;

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
        queue = new QueueService(2, handlerRegistry);   // Manually constructing the queue (which is why there's no annotation above it earlier).
    }

    @Test
    void enqueue_shouldAddTask_toJobsMap() {
        // Init Task w/ no-args constructor (just checking to make sure it appears in the jobs map with correct details).
        Task t = new Task();
        // Only the id and type fields are truly necessary for identification and status checking:
        t.setId("Task-ArbitraryTestId");
        t.setType(TaskType.EMAIL);
        // DEBUG:+NOTE: No type field causes error I'm pretty sure. Not sure about id though (maybe it should -- come back and look into this later).
        queue.enqueue(t);

        assertEquals(TaskStatus.QUEUED, t.getStatus()); // <-- DEBUG: Not 100% sure about this check...? (t's type set to QUEUED happens inside enqueue(...). Does this exceute sequentially?
        assertEquals(1, queue.getJobMapCount());    // Each test runs in isolation, so 1 job w/ only one task queued.
        assertNotNull(queue.getJobById("Task-ArbitraryTestId"));
        /* NOTE: Tasks of Type EMAIL take ~2 seconds to finish execution (after which, their Type becomes COMPLETED).
        I could potentially make this thread sleep for 2-3 seconds and then check after to see if the type becomes COMPLETED.
        But I think it's supposed to be bad practice doing that in Unit Tests since they're meant to be very quick...
        */
    }

    @Test
    void clear_shouldEmpty_theJobsMap() {
        // Init Task w/ no-args constructor:
        Task t = new Task();
        t.setId("Task-ArbitraryTestId");
        t.setType(TaskType.EMAIL);
        queue.enqueue(t);   // enqueue this job just so that the clear method can be invoked.

        // DEBUG: Not sure if this would work honestly. Get some clarity on if the execution of this test would be sequential (it should be right? Why am I questioning this?)
        queue.clear();
        assertEquals(0, queue.getJobMapCount());
        assertNull(queue.getJobById("Task-ArbitraryTestId"), ()->"The value returned by getJobById() should be null post-clear()");
    }

    @Test
    void delete_shouldRemoveJob_fromJobsMap() {
        // Init Task w/ no-args constructor:
        Task t = new Task();
        t.setId("Task-ArbitraryTestId");
        t.setType(TaskType.EMAIL);
        queue.enqueue(t);

        queue.deleteJob("Task-ArbitraryTestId");
        assertEquals(0, queue.getJobMapCount());
        assertNull(queue.getJobById("Task-ArbitraryTestId"), ()-> "The value returned by getJobById(\"id\") should be null after deleting the job identified by \"id\"");
    }

    @Test
    void retry_shouldEnqueue_failedTask() throws InterruptedException {
        // Init Task w/ no-args constructor:
        Task t = new Task();
        t.setId("Task-ArbitraryTestId");
        t.setType(TaskType.EMAIL);
        t.setStatus(TaskStatus.FAILED);
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
        queue.retry(t, 10);
        TimeUnit.MILLISECONDS.sleep(20);
        /* In the context of this text below, I think we can just it check if the TaskStatus is COMPLETED (we're defining the
        handler behavior in the when...thenReturn(); method above anyways; I could have made it be QUEUED instead). */

        /* After originally having .COMPLETED in the when...thenReturn(); function above, I've picked up that the minor delays
        I enforce in this test delay it enough that the queued Worker reaches the stage where its Task Status is set to INPROGRESS.
        So, I may as well just also have the when...thenReturn(); function set the same (since we just want to make sure that it
        gets re-enqueued regardless of what the status is when we check). */
        assertEquals(TaskStatus.INPROGRESS, queue.getJobById("Task-ArbitraryTestId").getStatus());
        assertEquals(1, queue.getJobMapCount());
    }

    @Test
    void retry_shouldReject_nonFailedTask() {
        Task t = new Task();
        t.setId("Task-ArbitraryTestId");
        t.setType(TaskType.EMAIL);
        t.setStatus(TaskStatus.COMPLETED);  // .retry(...) should reject tasks/jobs of status non-FAILED.
        queue.retry(t, 10);
        // I think we can omit the TimeUnit...sleep and when...thenReturn because we're expecting auto-rejection.

        assertNull(queue.getJobById("Task-ArbitraryTestId"));
        assertEquals(0, queue.getJobMapCount());
    }

}
