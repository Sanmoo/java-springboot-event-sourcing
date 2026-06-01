package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class AuthorizationNotFoundException extends DomainException {
    public AuthorizationNotFoundException(String message) {
        super(message);
    }
}
