package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.time.Duration;

public interface ProjectionConfig {
    String getWorkerId();
    int getBatchSize();
    int getMaxConsecutiveEventsPerAggregate();
    Duration getMaxDrainDuration();
    Duration getProcessingTimeout();
    Duration getInitialBackoff();
    Duration getMaxBackoff();
    int getMaxAttempts();
}
