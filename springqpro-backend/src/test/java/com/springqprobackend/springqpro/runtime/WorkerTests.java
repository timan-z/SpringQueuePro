package com.springqprobackend.springqpro.runtime;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.handlers.TaskHandler;
import com.springqprobackend.springqpro.models.Task;
import com.springqprobackend.springqpro.models.TaskHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/* NOTE: This function got much simpler after adding the TaskHandlerRegistry and global exception
handler. Writing tests should be relatively simple.
Again:
- More on different assert methods: https://www.baeldung.com/junit-assertions
*/

@ExtendWith(MockitoExtension.class)
public class WorkerTests {
    @Mock
    private TaskHandlerRegistry handlerRegistry;
    @Mock
    private TaskHandler mockHandler;

    private Task t;
    private Worker worker;

    @BeforeEach
    void setUp() {
        t = new Task();
        t.setId("Task-ArbitraryTaskId");
        t.setType(TaskType.EMAIL);
        t.setMaxRetries(3); // DEBUG: Shouldn't be necessary but doesn't hurt to have (?)
        when(handlerRegistry.getHandler("EMAIL")).thenReturn(mockHandler);
        worker = new Worker(t, handlerRegistry);
    }

    @Test
    void testWorker_runsAndInvokes_correctHandler() throws Exception {
        worker.run();
        verify(mockHandler, times(1)).handle(eq(t));
        // "eq" supposed to guarantee that the argument passed when the handle method occurred was equal to "t".
    }

    @Test
    void testWorker_whenInterrupted_handlesException() throws Exception {
        doThrow(new RuntimeException("NO IT DIDN'T IT HANDLED THE ERROR JUST RIGHT!!!")).when(mockHandler).handle(any(Task.class));
        worker.run();
        assertEquals(TaskStatus.FAILED, t.getStatus());
    }

}
