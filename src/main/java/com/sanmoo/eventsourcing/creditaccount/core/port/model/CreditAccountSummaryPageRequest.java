package com.sanmoo.eventsourcing.creditaccount.core.port.model;

public record CreditAccountSummaryPageRequest(int page, int size, String status) {
    public CreditAccountSummaryPageRequest(int page, int size) {
        this(page, size, null);
    }
}
