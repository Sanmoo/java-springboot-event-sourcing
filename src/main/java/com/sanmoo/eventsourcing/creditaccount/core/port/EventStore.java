package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface EventStore {
    List<EventEnvelope> loadEvents(String aggregateType, String aggregateId);
    AccountEventPage readAccountEvents(UUID accountId, long fromVersion, int limit);
    AppendResult appendEvents(String aggregateType, String aggregateId, long expectedVersion, List<CreditAccountEvent> events, Map<String, String> metadata);
}
