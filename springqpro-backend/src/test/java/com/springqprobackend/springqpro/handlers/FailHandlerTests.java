package com.springqprobackend.springqpro.handlers;

/* NOTE: Testing my FailHandler implementation of the TaskHandler class is a little bit tricky.
There are two things in my Handler classes that cause nondeterministic behavior (tests can't reliably assert outcomes).
The first is the Thread.sleep statement, which I've abstracted and taken care of w/ the "RealSleeper" model. Now, I need
to take care of the new Random() variable I have in my FailHandler class.
-- To do this, I've adjusted my FailHandler class to take a new argument in constructor initialization.
Now, instead of declaring a new Random() variable in function, it's a class field instead and will be dependency injected
by Spring. I'll have two constructors (both with the same arguments), but the one that's invoked depends on the number
of arguments provided during object initialization. (And this is good too, because I don't have to interfere w/ Worker's
logic where I need to adjust how the handler is invoked).
*/

import com.springqprobackend.springqpro.config.TaskHandlerProperties;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FailHandlerTests {
    @Mock
    private QueueService queue;
    @Mock
    private TaskHandlerProperties props;

    private Sleeper fastSleeper;
    private Sleeper mockSleeper;
    private FailHandler handler;
    private Task t;
    private Random fixedRandom;

    @BeforeEach
    void setUp() {
        when(props.getFailSleepTime()).thenReturn(1000L);
        when(props.getFailSuccSleepTime()).thenReturn(2000L);
        fastSleeper = millis -> {}; // Define functional interface implementation w/ Lambda. (Remember this).
        t = new Task();
        t.setId("Task-ArbitraryTastkId");
        t.setAttempts(0);
        t.setMaxRetries(3);
        fixedRandom = mock(Random.class);
        mockSleeper = mock(Sleeper.class);  // both mock sleepers will be used for different methods.
    }

    @Test
    void failHandler_completes_failedTask() throws InterruptedException {
        /* In my FailHandler.java class, I define successOdds as 0.25, so if I guarantee fixedRandom.nextDouble returns 0.1
        (which obv <= 0.25, so I'm in the % interval where my random odds succeeded), then I can let failHandler complete this task: */
        when(fixedRandom.nextDouble()).thenReturn(0.1);
        FailHandler failHandler = new FailHandler(queue, fastSleeper, fixedRandom, props);

        failHandler.handle(t);
        // Assertions:
        assertEquals(TaskStatus.COMPLETED, t.getStatus());
        verify(queue, never()).retry(any(Task.class), anyInt());
    }

    // This test case basically verifies that queue.retry(...) was called (when t.getAttempts() < t.getMaxRetries() and odds indicate no success).
    @Test
    void failHandler_retries_failedTask() throws InterruptedException {
        when(fixedRandom.nextDouble()).thenReturn(0.9);
        // (Dunno if needed)DEBUG: when(queue.retry(any(Task.class),anyLong()).then???
        FailHandler failHandler = new FailHandler(queue, fastSleeper, fixedRandom, props);
        // Remember that t.setAttempts() == 0 and t.getMaxRetries() == 3 (there's still room for more attempts, so this case works fine).

        failHandler.handle(t);
        assertEquals(TaskStatus.FAILED, t.getStatus());
        verify(queue, times(1)).retry(eq(t), eq(1000L));
    }

    // This test case confirms that queue.retry(...) is not ran when (t.getAttempts >= t.getMaxRetries() and odds indicate no success).
    @Test
    void failHandler_retires_maxFailedTask() throws InterruptedException {
        t.setAttempts(3);   // So, it goes straight to the failed if-condition branch.
        when(fixedRandom.nextDouble()).thenReturn(0.9);
        FailHandler failHandler = new FailHandler(queue, fastSleeper, fixedRandom, props);
        failHandler.handle(t);
        assertEquals(TaskStatus.FAILED, t.getStatus());
        verify(queue, never()).retry(eq(t), eq(1000L));
    }

    // More misc Tests:
    // Verify that the Sleeper.sleep(2000) call was invoked for a successful attempt:
    @Test
    void failHandler_callsSleep_onSuccess() throws InterruptedException {
        when(fixedRandom.nextDouble()).thenReturn(0.1);
        FailHandler failHandler = new FailHandler(queue, mockSleeper, fixedRandom, props);
        failHandler.handle(t);
        verify(mockSleeper, times(1)).sleep(2000L);
    }
    // Verify that the Sleeper.sleep(1000) call happened on permanent failure branch:
    @Test
    void failHandler_callsSleep_onPermFail() throws InterruptedException {
        t.setAttempts(3);
        when(fixedRandom.nextDouble()).thenReturn(0.9);
        FailHandler failHandler = new FailHandler(queue, mockSleeper, fixedRandom, props);
        failHandler.handle(t);
        verify(mockSleeper, times(1)).sleep(1000L);
    }
}
