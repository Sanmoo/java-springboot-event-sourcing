package com.sanmoo.eventsourcing.creditaccount.application.port;

import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import java.util.List;
import java.util.Map;

public interface EventStorePort {
    List<EventEnvelope> loadEvents(String aggregateType, String aggregateId);
    AppendResult appendEvents(String aggregateType, String aggregateId, long expectedVersion, List<CreditAccountEvent> events, Map<String, String> metadata);
}
