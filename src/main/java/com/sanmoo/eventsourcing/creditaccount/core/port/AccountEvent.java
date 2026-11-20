package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.time.Instant;
import java.util.Map;

public record AccountEvent(long version, String type, Instant occurredAt, Map<String, Object> payload) {
    public AccountEvent {
        payload = Map.copyOf(payload);
    }
}
