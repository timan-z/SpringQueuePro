package com.springqprobackend.springqpro.models;

/* NOTE: File TaskHandlerRegistry.java has no external dependencies beyond the map where classes that
implement the interface are injected. That means that no mocks are needed here (other than maybe
mocking the handlers themselves). */

import com.springqprobackend.springqpro.handlers.TaskHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/* All that needs to be tested is:
- Make sure that the correct handler is returned for a specific key (e.g., EmailHandler if getHandler("EMAIL")).
- Returns DefaultHandler if type is missing.
- getAllHandlers() exposes all of the handlers.
*/
class TaskHandlerRegistryTests {

    @Mock
    private TaskHandler defaultHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getHandler_returnsCorrectHandler_whenHandlerExists() {
        TaskHandler emailHandler = mock(TaskHandler.class);

        Map<String, TaskHandler> handlers = new HashMap<>();
        handlers.put("DEFAULT", defaultHandler);
        handlers.put("EMAIL", emailHandler);

        TaskHandlerRegistry registry = new TaskHandlerRegistry(handlers);

        assertThat(registry.getHandler("EMAIL")).isEqualTo(emailHandler);
    }

    @Test
    void getHandler_fallsBackToDefault_whenHandlerIsUnknown() {
        Map<String, TaskHandler> handlers = Map.of(
                "DEFAULT", defaultHandler
        );

        TaskHandlerRegistry registry = new TaskHandlerRegistry(handlers);

        assertThat(registry.getHandler("IDONTKNOW")).isEqualTo(defaultHandler);
    }

    @Test
    void getAllHandlers_returnsInjectedHandlerMap() {
        Map<String, TaskHandler> handlers = Map.of(
                "DEFAULT", defaultHandler
        );

        TaskHandlerRegistry registry = new TaskHandlerRegistry(handlers);

        assertThat(registry.getAllHandlers()).isEqualTo(handlers);
    }
}
