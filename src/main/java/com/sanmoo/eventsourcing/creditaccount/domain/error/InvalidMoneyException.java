package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class InvalidMoneyException extends DomainException {
    public InvalidMoneyException(String message) {
        super(message);
    }
}
