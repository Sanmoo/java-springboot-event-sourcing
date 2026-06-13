package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxDelivery;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxDeliveryRepository {
    List<OutboxDelivery> claimPending(String consumerName, String workerId, int batchSize);

    Optional<OutboxDelivery> claimNextForAggregate(
            String consumerName,
            String workerId,
            String aggregateType,
            String aggregateId,
            long expectedVersion);

    void markProcessed(UUID eventId, String consumerName);

    void markBlocked(UUID eventId, String consumerName, String reason);

    void markRetryableFailure(UUID eventId, String consumerName, int newAttempts, int maxAttempts, String error, Duration backoff);

    void markPermanentFailure(UUID eventId, String consumerName, int attempts, String error);

    void unblockNextVersion(String consumerName, String aggregateType, String aggregateId, long version);

    List<OutboxDelivery> findStaleProcessing(Duration timeout, int limit);

    int recoverStaleProcessing(Duration timeout, int limit);

    int insertDeliveriesForEvent(OutboxEvent event, int defaultMaxAttempts);
}
