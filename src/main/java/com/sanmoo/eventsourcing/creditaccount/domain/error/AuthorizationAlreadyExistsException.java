package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class AuthorizationAlreadyExistsException extends DomainException {
    public AuthorizationAlreadyExistsException(String message) {
        super(message);
    }
}
