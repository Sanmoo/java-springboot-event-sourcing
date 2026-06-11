package com.sanmoo.eventsourcing.creditaccount.projection;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "credit-account.projections")
public class ProjectionProperties {
    private boolean enabled = true;
    private java.time.Duration pollInterval = java.time.Duration.ofSeconds(1);
    private int batchSize = 50;
    private int maxAttempts = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public java.time.Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(java.time.Duration pollInterval) { this.pollInterval = pollInterval; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
}
