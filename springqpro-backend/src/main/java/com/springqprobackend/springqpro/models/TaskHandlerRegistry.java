package com.springqprobackend.springqpro.models;

import com.springqprobackend.springqpro.handlers.TaskHandler;
import org.springframework.stereotype.Component;
import java.util.Map;

/* NOTE: Instead of just having a "private final Map<String, TaskHandler> handlers;" field inside my Worker.java class,
I'm going to have this TaskHandlerRegister component that'll automatically collect all the TaskHandler beans and
provide easy lookup methods (that I'll obviously define in this class).

This is better than having, like, a Map<String,TaskHandler> handlers field in my Worker.java class. (Which is what
I originally had, but it ended up being pretty problematic when attempting to instantiate new Task types elsewhere.
Like I couldn't do new Worker(task, this, new Map<String,TaskHandler>()); because that handlers map is meant to be
a Spring-managed collection of beans, not something to instantiate).
(And so TaskHandlerRegistry is basically an intermediary/abstraction for that aspect).
*/

@Component
public class TaskHandlerRegistry {
    /* This new field added below relates to my move away from the switch-case handling of jobs/tasks based on type
    and towards handling them using external classes that implement an interface that'll be managed by this map below: */

    private final Map<String,TaskHandler> handlers;
    // Spring will automatically inject all beans that implement TaskHandler using @Component("TYPE") as the key.
    public TaskHandlerRegistry(Map<String, TaskHandler> handlers) {
        this.handlers = handlers;
    }
    public TaskHandler getHandler(String type) {
        //return handlers.get(type);
        return handlers.getOrDefault(type, handlers.get("DEFAULT"));    // <-- DEBUG: TO HANDLE MISC / UNRECOGNIZED JOB/TASK TYPES.
    }
    public Map<String, TaskHandler> getAllHandlers() {
        return handlers;
    }
}
