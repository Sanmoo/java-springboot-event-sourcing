package com.sanmoo.eventsourcing.creditaccount.core.error;

import com.sanmoo.eventsourcing.creditaccount.domain.error.DomainException;

public class InvalidEventHistoryPageException extends DomainException {
    public InvalidEventHistoryPageException(String message) {
        super(message);
    }
}
