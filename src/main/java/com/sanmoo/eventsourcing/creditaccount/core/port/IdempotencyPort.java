package com.sanmoo.eventsourcing.creditaccount.core.port;

public interface IdempotencyPort {
    IdempotencyDecision start(String key, String commandType, String aggregateId, String requestHash);
    void complete(String key, String responsePayload);
}
