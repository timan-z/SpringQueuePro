package com.springqprobackend.springqpro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/* QueueProperties.java
--------------------------------------------------------------------------------------------------
[HISTORY]:
When retry logic, parallelism, and sleep durations were hardcoded, tuning the
queue meant editing Java source. QueueProperties was introduced so these values
could be configured externally.

[CURRENT ROLE]:
Maps application.yml values for:
  - executor thread counts
  - scheduled executor workers
  - default delays
  - retry policies
These values directly affect ProcessingService + QueueService behavior.

[FUTURE WORK]:
CloudQueue might:
  - dynamically tune these values based on load
  - store them in AWS Parameter Store or Secrets Manager
--------------------------------------------------------------------------------------------------
*/

@ConfigurationProperties(prefix="queue")
@Component
public class QueueProperties {
    private int mainExecWorkerCount;
    private int schedExecWorkerCount;

    // getters:
    public int getMainExecWorkerCount() {
        return mainExecWorkerCount;
    }
    public int getSchedExecWorkerCount() {
        return schedExecWorkerCount;
    }
    // setters:
    public void setMainExecWorkerCount(int mainExecWorkerCount) {
        this.mainExecWorkerCount = mainExecWorkerCount;
    }
    public void setSchedExecWorkerCount(int schedExecWorkerCount) {
        this.schedExecWorkerCount = schedExecWorkerCount;
    }
}
