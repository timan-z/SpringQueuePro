package com.springqprobackend.springqpro.interfaces;

import com.springqprobackend.springqpro.models.Task;

/* TaskHandler.java
--------------------------------------------------------------------------------------------------
This is the Interface implemented by all of my Task Handlers, all of which need to define and flesh
out the following operation: "void handle(Task task) throws Exception".

[HISTORY]
During the prototype "base-level" SpringQueue project, there was no interface and every Handler was a
tightly coupled individual file that would change Task status (e.g., QUEUED -> INPROGRESS -> COMPLETED)
themselves and invoke retry() themselves if necessary. This was changed to use an interface for modularity
and to decouple functionality (the actual handlers are quite primitive, it's designed to be reworked).

At this point, they're more like simulate worktime vessels as ProcessingService handles all persistence updates,
retries, metrics, and state transitions.

[SPECIAL NOTE]:
The FAIL handler family simulates intentional failure/retry scenarios.

[FUTURE WORK]:
- In CloudQueue, all the handlers will probably be replaced by microservices or remote workers.
--------------------------------------------------------------------------------------------------
*/

// 2025-11-30-NOTE: Save this large comment block below for my overall README.md (and probably my detailed "phases" section too).
/* NOTE: Remember that my end goal for this whole SpringQueue(->SpringQueuePro->CloudQueue) project is for it
to eventually be deployed on the cloud.

In my Worker.java class, I have a large switch-case block that handles different tasks. That was fine for my
SpringQueue and GoQueue prototype, but it's brittle and better to change that to a pluggable handler pattern for SpringQueuePro.
So, that's basically going to be this TaskHandler interface that gets implemented by specific handlers for each job type.
(For instance, I'll have @Component("EMAIL") EmailHandler be a class that implements TaskHandler). This is better for modularity too since
my choices for actual job types e.g., email were extremely arbitrary and I will probably end up changing a lot of them later on.
-- All of them are basically the same right now... (near identical func each time) but this might change as the project advances,
which is another reason why adopting this approach is good.
-- So each implementation of the TaskHandler interface will be a @Component, these handler classes will be wired into
a Map<String,TaskHandler> variable inside Worker automatically by Spring. (So, extending on the last bullet point, adding new task types
is as easy as just adding a new @Component class.

More benefits of this choice:
- O(1) extension (better than switch/case efficiency wise).
- Independent testing of each task handler.
- Cleaner separation of concerns! (This is important).
See comment block in TaskHandlerRegistry.java.
*/
public interface TaskHandler {
    void handle(Task task) throws InterruptedException;
}
