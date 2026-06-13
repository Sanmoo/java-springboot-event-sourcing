package com.sanmoo.eventsourcing.creditaccount.core.projection;

public enum OutboxDeliveryStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    BLOCKED,
    FAILED
}
