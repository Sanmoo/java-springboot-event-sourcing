package com.sanmoo.eventsourcing.creditaccount.core.error;

import com.sanmoo.eventsourcing.creditaccount.domain.error.DomainException;

public class InvalidPageSizeException extends DomainException {
    public InvalidPageSizeException(String message) { super(message); }
}
