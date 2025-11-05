package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.enums.TaskType;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.models.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/* NOTE: I basically only have two types of Handlers defined as of this moment.
The variation you see in DefaultHandler mimicked across other Handlers w/ different sleep times.
-- Picking DefaultHandler specifically since it's the *default* Handler I'll always have regardless of refactoring, etc.
*/
public class DefaultHandlerTests {
    private Sleeper fastSleeper;
    private DefaultHandler handler;
    private Task t;

    @BeforeEach
    void setUp() {
        /* NOTE: Very important is the line of code beneath this comment!
        We're basically overriding the "RealSleeper" implementation of the Sleeper interface here
        by using fastSleeper instead. Below is a lambda expression that implements Sleeper (functional interface),
        which says that this Sleeper implementation takes some "long" var and just does nothing (doesn't actually sleep).
        So we essentially skip the .sleep part of the Handler execution and skip straight to the followup logic. */
        fastSleeper = millis -> {};
        handler = new DefaultHandler(fastSleeper);
        t = new Task();
        t.setId("Task-ArbitraryTaskId");
        t.setType(TaskType.valueOf("TEST"));
    }

    @Test
    void handle_shouldSet_TaskCompleted() throws InterruptedException {
        handler.handle(t);
        assertEquals(TaskStatus.COMPLETED, t.getStatus());
    }
}
