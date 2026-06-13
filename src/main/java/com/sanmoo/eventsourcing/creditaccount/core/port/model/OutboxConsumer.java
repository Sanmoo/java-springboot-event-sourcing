package com.sanmoo.eventsourcing.creditaccount.core.port.model;

import java.time.Instant;

public record OutboxConsumer(
        String consumerName,
        String description,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}
