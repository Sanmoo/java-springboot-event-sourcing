package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class AccountNotFoundException extends DomainException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
