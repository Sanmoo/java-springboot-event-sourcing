package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class CreditLimitAlreadyAssignedException extends DomainException {
    public CreditLimitAlreadyAssignedException(String message) {
        super(message);
    }
}
