package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class AuthorizationNotOpenException extends DomainException {
    public AuthorizationNotOpenException(String message) {
        super(message);
    }
}
