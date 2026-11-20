package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.time.Instant;
import java.util.Map;

public record AccountEventResponse(long version, String type, Instant occurredAt, Map<String, Object> payload) {
    public AccountEventResponse {
        payload = Map.copyOf(payload);
    }
}
