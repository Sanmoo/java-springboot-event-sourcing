package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class InsufficientAvailableLimitException extends DomainException {
    public InsufficientAvailableLimitException(String message) {
        super(message);
    }
}
