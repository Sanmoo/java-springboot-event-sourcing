package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class PaymentExceedsOutstandingBalanceException extends DomainException {
    public PaymentExceedsOutstandingBalanceException(String message) {
        super(message);
    }
}
