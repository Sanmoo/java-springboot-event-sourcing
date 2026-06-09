package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.Optional;

/**
 * Port (hexagonal architecture output port / SPI) for idempotency handling.
 * <p>
 * Implementations provide a distributed locking mechanism and persistent storage
 * for idempotency records, ensuring that the same command is processed at most once
 * within a transactional boundary.
 */
public interface IdempotencyPort {
    /**
     * Acquires an exclusive transaction-scoped lock for the given idempotency key.
     * Blocks concurrent requests with the same key until the current transaction completes.
     *
     * @throws com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException if the lock cannot be acquired before the configured timeout
     */
    void lockKey(String key);

    /**
     * Finds a completed idempotency result by key.
     * Returns Optional.empty() if no completed result exists for this key
     * (either because the command was never executed or its transaction rolled back).
     * A completed result indicates the command was applied and can be replayed.
     */
    Optional<IdempotencyRecord> findByKey(String key);

    /**
     * Persists a completed command result for future replay.
     * Must be called within the same transaction that appended the command's events.
     */
    void saveResult(
            String key,
            String commandType,
            String aggregateId,
            String requestHash,
            String responsePayload,
            long aggregateVersion
    );
}
