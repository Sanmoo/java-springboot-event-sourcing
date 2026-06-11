package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.Optional;

public interface IdempotencyRepository {
    void lockKey(String idempotencyKey);
    Optional<IdempotencyRecord> findByKey(String idempotencyKey);
    void saveResult(String idempotencyKey, String commandType, String aggregateId, String requestHash, String responsePayload, long aggregateVersion);
}
