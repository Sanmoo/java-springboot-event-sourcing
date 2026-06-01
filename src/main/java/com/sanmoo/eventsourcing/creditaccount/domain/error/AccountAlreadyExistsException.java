package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class AccountAlreadyExistsException extends DomainException {
    public AccountAlreadyExistsException(String message) {
        super(message);
    }
}
