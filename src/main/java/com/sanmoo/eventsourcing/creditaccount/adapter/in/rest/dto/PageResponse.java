package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.util.List;

public record PageResponse(
        List<CreditAccountResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
