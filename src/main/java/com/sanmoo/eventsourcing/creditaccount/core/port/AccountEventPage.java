package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.List;

public record AccountEventPage(List<AccountEvent> events, Long nextFromVersion, boolean accountExists) {
    public AccountEventPage {
        events = List.copyOf(events);
    }
}
