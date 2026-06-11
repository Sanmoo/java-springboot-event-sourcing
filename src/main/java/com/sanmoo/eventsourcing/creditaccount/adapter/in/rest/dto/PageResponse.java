package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.util.List;
import java.util.Map;

public record PageResponse(
        List<Map<String, Object>> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
