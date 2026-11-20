package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AccountEventResponse(long version, String type, Instant occurredAt, Map<String, Object> payload) {
    public AccountEventResponse {
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
