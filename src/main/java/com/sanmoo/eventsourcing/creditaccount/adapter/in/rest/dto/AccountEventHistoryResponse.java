package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.util.List;

public record AccountEventHistoryResponse(List<AccountEventResponse> items, Long nextFromVersion) {
    public AccountEventHistoryResponse {
        items = List.copyOf(items);
    }
}
