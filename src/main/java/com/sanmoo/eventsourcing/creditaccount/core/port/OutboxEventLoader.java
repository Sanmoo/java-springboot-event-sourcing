package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;

import java.util.Optional;
import java.util.UUID;

public interface OutboxEventLoader {
    Optional<OutboxEvent> findById(UUID eventId);
}
