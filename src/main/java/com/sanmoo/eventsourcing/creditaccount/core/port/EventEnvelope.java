package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
    UUID eventId,
    String aggregateType,
    String aggregateId,
    long aggregateVersion,
    CreditAccountEvent event,
    Instant occurredAt,
    Map<String, String> metadata
) {}
