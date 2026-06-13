package com.sanmoo.eventsourcing.creditaccount.core.port.model;

import java.time.Instant;
import java.util.UUID;

public record ProjectionCheckpoint(
        String projectionName,
        String aggregateType,
        String aggregateId,
        long lastProjectedVersion,
        UUID lastEventId,
        Instant updatedAt
) {}
