package com.sanmoo.eventsourcing.creditaccount.projection;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "credit-account.projections")
public class ProjectionProperties {
    private boolean enabled = true;
    private Duration pollInterval = Duration.ofSeconds(1);
    private int batchSize = 50;
    private int maxAttempts = 10;
    private Duration initialBackoff = Duration.ofSeconds(10);
    private Duration maxBackoff = Duration.ofMinutes(15);
    private int maxConsecutiveEventsPerAggregate = 100;
    private Duration maxDrainDuration = Duration.ofSeconds(5);
    private Duration processingTimeout = Duration.ofMinutes(2);
    private String workerId = "worker-" + UUID.randomUUID();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    public int getMaxConsecutiveEventsPerAggregate() { return maxConsecutiveEventsPerAggregate; }
    public void setMaxConsecutiveEventsPerAggregate(int v) { this.maxConsecutiveEventsPerAggregate = v; }
    public Duration getMaxDrainDuration() { return maxDrainDuration; }
    public void setMaxDrainDuration(Duration v) { this.maxDrainDuration = v; }
    public Duration getProcessingTimeout() { return processingTimeout; }
    public void setProcessingTimeout(Duration v) { this.processingTimeout = v; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
}
