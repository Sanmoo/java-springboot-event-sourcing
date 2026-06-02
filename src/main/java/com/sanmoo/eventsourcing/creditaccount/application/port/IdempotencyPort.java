package com.sanmoo.eventsourcing.creditaccount.application.port;

public interface IdempotencyPort {
    IdempotencyDecision start(String key, String commandType, String aggregateId, String requestHash);
    void complete(String key, String responsePayload);
}
