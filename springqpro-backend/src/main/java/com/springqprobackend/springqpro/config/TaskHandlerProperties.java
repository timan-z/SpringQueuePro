package com.springqprobackend.springqpro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix="t-handler")
@Component
public class TaskHandlerProperties {
    private long defaultSleepTime;
    private long dataCleanUpSleepTime;
    private long emailSleepTime;
    private long failSleepTime;
    private long failSuccSleepTime;
    private long failAbsSleepTime;
    private long newsLetterSleepTime;
    private long reportSleepTime;
    private long smsSleepTime;
    private long takesLongSleepTime;

    // getters:
    public long getDefaultSleepTime() { return defaultSleepTime; }
    public long getDataCleanUpSleepTime() { return dataCleanUpSleepTime; }
    public long getEmailSleepTime() { return emailSleepTime; }
    public long getFailSleepTime() { return failSleepTime; }
    public long getFailSuccSleepTime() { return failSuccSleepTime; }
    public long getFailAbsSleepTime() { return failAbsSleepTime; }
    public long getNewsLetterSleepTime() { return newsLetterSleepTime; }
    public long getReportSleepTime() { return reportSleepTime; }
    public long getSmsSleepTime() { return smsSleepTime; }
    public long getTakesLongSleepTime() { return takesLongSleepTime; }
    // setters:
    public void setDefaultSleepTime(long defaultSleepTime) { this.defaultSleepTime = defaultSleepTime; }
    public void setDataCleanUpSleepTime(long dataCleanUpSleepTime) { this.dataCleanUpSleepTime = dataCleanUpSleepTime; }
    public void setEmailSleepTime(long emailSleepTime) { this.emailSleepTime = emailSleepTime; }
    public void setFailSleepTime(long failSleepTime) { this.failSleepTime = failSleepTime; }
    public void setFailSuccSleepTime(long failSuccSleepTime) { this.failSuccSleepTime = failSuccSleepTime; }
    public void setFailAbsSleepTime(long failAbsSleepTime) { this.failAbsSleepTime = failAbsSleepTime; }
    public void setNewsLetterSleepTime(long newsLetterSleepTime) { this.newsLetterSleepTime = newsLetterSleepTime; }
    public void setReportSleepTime(long reportSleepTime) { this.reportSleepTime = reportSleepTime; }
    public void setSmsSleepTime(long smsSleepTime) { this.smsSleepTime = smsSleepTime; }
    public void setTakesLongSleepTime(long takesLongSleepTime) { this.takesLongSleepTime = takesLongSleepTime; }
}
