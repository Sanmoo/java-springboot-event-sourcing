package com.sanmoo.eventsourcing.creditaccount.core.port.model;

import java.util.List;

public record CreditAccountSummaryPage(
        List<CreditAccountSummary> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
