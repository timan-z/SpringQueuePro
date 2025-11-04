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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/* NOTE(S)-TO-SELF: QueueService is a @Service, so I don't know if that means I need to use
the @ExtendWith() function or not?
- More on different assert methods: https://www.baeldung.com/junit-assertions
*/

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

        queue.enqueue(t);

        assertEquals(TaskStatus.QUEUED, t.getStatus()); // <-- DEBUG: Not 100% sure about this check...? (t's type set to QUEUED happens inside enqueue(...). Does this exceute sequentially?
        assertEquals(1, queue.getJobMapCount());    // Each test runs in isolation, so 1 job w/ only one task queued.
        assertNotNull(queue.getJobById("Task-ArbitraryTestId"));
        /* NOTE: Tasks of Type EMAIL take ~2 seconds to finish execution (after which, their Type becomes COMPLETED).
        I could potentially make this thread sleep for 2-3 seconds and then check after to see if the type becomes COMPLETED.
        But I think it's supposed to be bad practice doing that in Unit Tests since they're meant to be very quick...
        */
    }

}
