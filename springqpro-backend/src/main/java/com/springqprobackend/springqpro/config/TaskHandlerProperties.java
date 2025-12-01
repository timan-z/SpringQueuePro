package com.springqprobackend.springqpro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/* TaskHandlerProperties.java
--------------------------------------------------------------------------------------------------
[HISTORY]:
Early handlers hardcoded sleep times directly inside the Java classes. As more
task types were added (EMAIL, FAIL, TAKESLONG, etc.), these constants had to be
externalized, configurable, and manageable.

This @ConfigurationProperties class was introduced to load handler timing values
from application.yml.

[CURRENT ROLE]:
Provides per-handler configurable values such as:
  - simulated processing time
  - fail-retry timing
  - long-running task timing

These properties improve realism and make load testing more consistent.

[FUTURE WORK]:
CloudQueue may:
  - load these values dynamically from Redis
  - expose them in an admin panel
  - adjust them adaptively under load
--------------------------------------------------------------------------------------------------
*/

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
