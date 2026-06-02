package com.sanmoo.eventsourcing.creditaccount.application.error;

public class ConcurrencyConflictException extends RuntimeException {
    public ConcurrencyConflictException(String aggregateType, String aggregateId, long expectedVersion) {
        super("Concurrency conflict on %s/%s at version %d".formatted(aggregateType, aggregateId, expectedVersion));
    }

    public ConcurrencyConflictException(String aggregateType, String aggregateId, long expectedVersion, Throwable cause) {
        super("Concurrency conflict on %s/%s at version %d".formatted(aggregateType, aggregateId, expectedVersion), cause);
    }
}
