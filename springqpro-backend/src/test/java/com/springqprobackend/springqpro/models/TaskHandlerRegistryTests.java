package com.springqprobackend.springqpro.models;

/* NOTE: File TaskHandlerRegistry.java has no external dependencies beyond the map where classes that
implement the interface are injected. That means that no mocks are needed here (other than maybe
mocking the handlers themselves). */

import com.springqprobackend.springqpro.handlers.TaskHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/* All that needs to be tested is:
- Make sure that the correct handler is returned for a specific key (e.g., EmailHandler if getHandler("EMAIL")).
- Returns DefaultHandler if type is missing.
- getAllHandlers() exposes all of the handlers.
*/
public class TaskHandlerRegistryTests {
    private TaskHandler defaultHandler;
    private Map<String, TaskHandler> map;

    @BeforeEach
    void setUp() {
        defaultHandler = mock(TaskHandler.class);
        //map = Map.of("DEFAULT", defaultHandler);
        map = new HashMap<>();
        map.put("DEFAULT", defaultHandler);
    }

    @Test
    void getHandler_returns_correctHandler() {
        TaskHandler emailHandler = mock(TaskHandler.class);
        /*TaskHandler defaultHandler = mock(TaskHandler.class);
        Map<String, TaskHandler> map = Map.of(
                "EMAIL", emailHandler,
                "DEFAULT", defaultHandler
        );*/
        map.put("EMAIL", emailHandler);
        TaskHandlerRegistry registry = new TaskHandlerRegistry(map);
        assertEquals(emailHandler, registry.getHandler("EMAIL"));
    }

    @Test
    void unknownHandler_defaultsTo_defaultHandler() {
        /*TaskHandler defaultHandler = mock(TaskHandler.class);
        Map<String, TaskHandler> map = Map.of(
                "DEFAULT", defaultHandler
        );*/
        TaskHandlerRegistry registry = new TaskHandlerRegistry(map);
        assertEquals(defaultHandler, registry.getHandler("IDONTKNOW"));
    }

    @Test
    void getAllHandlers_returns_injectedMap() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry(map);
        assertEquals(map, registry.getAllHandlers());
    }
}
