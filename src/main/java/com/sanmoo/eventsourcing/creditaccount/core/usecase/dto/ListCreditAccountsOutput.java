package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import java.util.List;

public record ListCreditAccountsOutput(
        List<CreditAccountOutput> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
