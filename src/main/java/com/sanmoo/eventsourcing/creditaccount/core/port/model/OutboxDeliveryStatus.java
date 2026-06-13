package com.sanmoo.eventsourcing.creditaccount.core.port.model;

public enum OutboxDeliveryStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    BLOCKED,
    FAILED
}
