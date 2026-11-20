package com.sanmoo.eventsourcing.creditaccount.domain.error;

public class AccountNotBlockedException extends DomainException {
    public AccountNotBlockedException(String message) {
        super(message);
    }
}
