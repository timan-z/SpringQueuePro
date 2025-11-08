package com.springqprobackend.springqpro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
