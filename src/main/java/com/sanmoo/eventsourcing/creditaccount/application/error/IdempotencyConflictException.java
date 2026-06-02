package com.sanmoo.eventsourcing.creditaccount.application.error;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
