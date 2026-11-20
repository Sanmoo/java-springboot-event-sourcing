package com.sanmoo.eventsourcing.creditaccount.domain.error;

public class AccountBlockedException extends DomainException {
    public AccountBlockedException(String message) {
        super(message);
    }
}
