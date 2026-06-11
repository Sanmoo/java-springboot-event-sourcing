package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {
    List<OutboxEvent> findPending(int limit);
    void markProcessed(UUID eventId);
    void markFailed(UUID eventId, String error);
}
