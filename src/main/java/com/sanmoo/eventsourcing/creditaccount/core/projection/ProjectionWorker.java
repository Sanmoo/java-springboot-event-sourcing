package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxDeliveryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventLoader;
import com.sanmoo.eventsourcing.creditaccount.core.port.ProjectionCheckpointRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.ProjectionConfig;
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxDelivery;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.ProjectionCheckpoint;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectionWorker {

    private static final Logger log = LoggerFactory.getLogger(ProjectionWorker.class);

    private final OutboxDeliveryRepository deliveries;
    private final CreditAccountSummaryRepository summaries;
    private final ProjectionCheckpointRepository checkpoints;
    private final OutboxEventLoader eventLoader;
    private final CreditAccountSummaryProjector projector;
    private final ProjectionGating gating;
    private final ProjectionConfig properties;
    private final TransactionRunner transactionRunner;

    public ProjectionWorkerResult processOnce(int batchSize) {
        List<OutboxDelivery> claimed = transactionRunner.runInTransaction(() ->
                deliveries.claimPending(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                        properties.getWorkerId(), batchSize));

        if (claimed == null || claimed.isEmpty()) {
            return ProjectionWorkerResult.empty();
        }

        ProjectionWorkerResult result = ProjectionWorkerResult.empty().withClaimed(claimed.size());

        List<OutboxDelivery> sorted = new ArrayList<>(claimed);
        sorted.sort(Comparator
                .comparing((OutboxDelivery d) -> loadEvent(d.eventId()).aggregateType())
                .thenComparing(d -> loadEvent(d.eventId()).aggregateId())
                .thenComparing(d -> loadEvent(d.eventId()).aggregateVersion()));

        Instant drainDeadline = Instant.now().plus(Duration.ofMillis(
                properties.getMaxDrainDuration().toMillis()));
        int consecutive = 0;

        for (OutboxDelivery delivery : sorted) {
            if (consecutive >= properties.getMaxConsecutiveEventsPerAggregate()
                    || Instant.now().isAfter(drainDeadline)) {
                break;
            }
            consecutive++;

            result = processOne(result, delivery);
        }

        return result;
    }

    private ProjectionWorkerResult processOne(ProjectionWorkerResult result, OutboxDelivery delivery) {
        try {
            return transactionRunner.runInTransaction(() -> processOneInTransaction(result, delivery));
        } catch (RuntimeException e) {
            return handleFailure(result, delivery, e);
        }
    }

    private ProjectionWorkerResult processOneInTransaction(ProjectionWorkerResult result, OutboxDelivery delivery) {
        OutboxEvent event = loadEvent(delivery.eventId());
        Optional<ProjectionCheckpoint> cp = checkpoints.find(
                ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                event.aggregateType(), event.aggregateId());

        ProjectionGatingResult gatingResult = gating.decide(
                ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(), event, cp);

        return switch (gatingResult.decision()) {
            case BLOCKED -> {
                deliveries.markBlocked(delivery.eventId(), delivery.consumerName(),
                        gatingResult.reason());
                yield result.plusBlocked(1);
            }
            case PERMANENT_FAILURE -> {
                deliveries.markPermanentFailure(delivery.eventId(), delivery.consumerName(),
                        delivery.processingAttempts() + 1, gatingResult.reason());
                yield result.plusFailed(1);
            }
            case ALREADY_APPLIED -> {
                deliveries.markProcessed(delivery.eventId(), delivery.consumerName());
                yield result.plusProcessed(1);
            }
            case APPLY -> applyProjection(result, delivery, event, cp);
        };
    }

    private ProjectionWorkerResult applyProjection(ProjectionWorkerResult result,
                                                   OutboxDelivery delivery,
                                                   OutboxEvent event,
                                                   Optional<ProjectionCheckpoint> cp) {
        CreditAccountId accountId = CreditAccountId.of(UUID.fromString(event.aggregateId()));
        Optional<CreditAccountSummary> current = summaries.findById(accountId);
        CreditAccountSummary base = current.orElseGet(() -> projector.emptySummary(event));
        CreditAccountSummary next = projector.apply(event, base);

        CreditAccountSummary withEventId = new CreditAccountSummary(
                next.creditAccountId(), next.opened(), next.creditLimit(),
                next.outstandingBalance(), next.authorizedAmount(), next.availableLimit(),
                next.authorizations(), next.projectedVersion(), event.eventId(), next.updatedAt());
        summaries.upsert(withEventId);

        checkpoints.upsert(new ProjectionCheckpoint(
                ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                event.aggregateType(), event.aggregateId(),
                event.aggregateVersion(), event.eventId(), Instant.now()));

        deliveries.markProcessed(delivery.eventId(), delivery.consumerName());
        deliveries.unblockNextVersion(delivery.consumerName(),
                event.aggregateType(), event.aggregateId(),
                event.aggregateVersion() + 1L);

        return result.plusProcessed(1);
    }

    private ProjectionWorkerResult handleFailure(ProjectionWorkerResult result,
                                                  OutboxDelivery delivery,
                                                  RuntimeException e) {
        int newAttempts = delivery.processingAttempts() + 1;
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();

        if (newAttempts >= delivery.maxAttempts()) {
            deliveries.markPermanentFailure(delivery.eventId(), delivery.consumerName(),
                    newAttempts, error);
            return result.plusFailed(1);
        }

        Duration backoff = computeBackoff(newAttempts);
        deliveries.markRetryableFailure(delivery.eventId(), delivery.consumerName(),
                newAttempts, delivery.maxAttempts(), error, backoff);
        return result.plusRetried(1);
    }

    private Duration computeBackoff(int attempts) {
        long initial = properties.getInitialBackoff().toSeconds();
        long max = properties.getMaxBackoff().toSeconds();
        long seconds = initial * (1L << Math.min(attempts - 1, 16));
        return Duration.ofSeconds(Math.min(seconds, max));
    }

    private OutboxEvent loadEvent(UUID eventId) {
        return eventLoader.findById(eventId).orElseThrow(
                () -> new IllegalStateException("Outbox event not found: " + eventId));
    }
}
