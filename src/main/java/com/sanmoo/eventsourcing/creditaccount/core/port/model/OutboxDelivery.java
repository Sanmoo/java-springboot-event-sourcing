package com.sanmoo.eventsourcing.creditaccount.core.port.model;

import com.sanmoo.eventsourcing.creditaccount.core.projection.OutboxDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record OutboxDelivery(
        UUID eventId,
        String consumerName,
        OutboxDeliveryStatus status,
        int processingAttempts,
        int maxAttempts,
        Instant nextAttemptAt,
        Instant lockedAt,
        String lockedBy,
        String lastError,
        String blockedReason,
        Instant blockedAt,
        Instant processedAt,
        Instant failedAt,
        Instant createdAt,
        Instant updatedAt
) {}
