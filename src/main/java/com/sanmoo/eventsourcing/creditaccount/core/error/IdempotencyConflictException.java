package com.sanmoo.eventsourcing.creditaccount.core.error;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
