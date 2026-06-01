package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class InvalidCreditLimitException extends DomainException {
    public InvalidCreditLimitException(String message) {
        super(message);
    }
}
