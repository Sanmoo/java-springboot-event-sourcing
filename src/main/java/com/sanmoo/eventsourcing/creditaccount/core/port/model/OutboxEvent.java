package com.sanmoo.eventsourcing.creditaccount.core.port.model;

import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String eventType,
        CreditAccountEvent event,
        Map<String, String> metadata,
        Instant occurredAt
) {}
