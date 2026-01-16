package com.springqprobackend.springqpro.handlers;

// 2025-11-30-NOTE: Basically shouldn't be testing Handlers anymore. This is kept for my documentation stuff.

import com.springqprobackend.springqpro.config.TaskHandlerProperties;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.util.Sleeper;
import com.springqprobackend.springqpro.models.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/* NOTE: I basically only have two types of Handlers defined as of this moment.
The variation you see in DefaultHandler mimicked across other Handlers w/ different sleep times.
-- Picking DefaultHandler specifically since it's the *default* Handler I'll always have regardless of refactoring, etc.
*/
@ExtendWith(MockitoExtension.class)
class DefaultHandlerTests {

    @Mock
    private Sleeper sleeper;

    @Mock
    private TaskHandlerProperties props;

    private DefaultHandler handler;

    private Task task;

    @BeforeEach
    void setUp() {
        when(props.getDefaultSleepTime()).thenReturn(2000L);
        handler = new DefaultHandler(sleeper, props);

        task = new Task();
        task.setId("Task-ArbitraryTaskId");
        task.setType(TaskType.EMAIL);
    }

    @Test
    void handle_shouldInvokeSleeperWithConfiguredDelay() throws InterruptedException {
        handler.handle(task);

        verify(sleeper).sleep(2000L);
    }

    @Test
    void handle_shouldNotThrow() {
        assertDoesNotThrow(() -> handler.handle(task));
    }
}
