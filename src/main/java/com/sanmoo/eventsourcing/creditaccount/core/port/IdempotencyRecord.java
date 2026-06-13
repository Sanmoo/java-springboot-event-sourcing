package com.sanmoo.eventsourcing.creditaccount.core.port;

public record IdempotencyRecord(
        String idempotencyKey,
        String commandType,
        String aggregateId,
        String requestHash,
        String responsePayload
) {}
