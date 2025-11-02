package com.springqprobackend.springqpro.interfaces;

import com.springqprobackend.springqpro.models.Task;

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
