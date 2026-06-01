package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class CreditLimitNotAssignedException extends DomainException {
    public CreditLimitNotAssignedException(String message) {
        super(message);
    }
}
