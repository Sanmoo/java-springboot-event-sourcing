package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.util.UUID;

public record ProjectionNotReadyResponse(
        String message,
        UUID creditAccountId,
        Long currentProjectionVersion,
        long requiredVersion
) {}
