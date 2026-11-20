package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GetCreditAccountEventsOutput(List<Item> items, Long nextFromVersion) {
    public GetCreditAccountEventsOutput {
        items = List.copyOf(items);
    }

    public record Item(long version, String type, Instant occurredAt, Map<String, Object> payload) {
        public Item {
            payload = Map.copyOf(payload);
        }
    }
}
