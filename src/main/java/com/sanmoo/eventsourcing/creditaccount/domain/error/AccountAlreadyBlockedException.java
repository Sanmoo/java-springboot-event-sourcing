package com.sanmoo.eventsourcing.creditaccount.domain.error;

public class AccountAlreadyBlockedException extends DomainException {
    public AccountAlreadyBlockedException(String message) {
        super(message);
    }
}
