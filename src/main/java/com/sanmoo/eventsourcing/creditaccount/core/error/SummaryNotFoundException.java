package com.sanmoo.eventsourcing.creditaccount.core.error;

public class SummaryNotFoundException extends RuntimeException {
    public SummaryNotFoundException(String message) { super(message); }
}
