package com.springqprobackend.springqpro.handlers;

import com.springqprobackend.springqpro.config.TaskHandlerProperties;
import com.springqprobackend.springqpro.enums.TaskStatus;
import com.springqprobackend.springqpro.interfaces.Sleeper;
import com.springqprobackend.springqpro.interfaces.TaskHandler;
import com.springqprobackend.springqpro.models.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("REPORT")
public class ReportHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReportHandler.class);
    private final Sleeper sleeper;
    private final TaskHandlerProperties props;

    public ReportHandler(Sleeper sleeper, TaskHandlerProperties props) {
        this.sleeper = sleeper;
        this.props = props;
    }

    @Override
    public void handle(Task task) throws InterruptedException {
        sleeper.sleep(props.getReportSleepTime());
        task.setStatus(TaskStatus.COMPLETED);
        logger.info("Task {} (Type: {}) completed", task.getId(), task.getType());
    }
}
