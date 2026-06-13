package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.ProjectionCheckpointRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.ProjectionCheckpoint;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectionWorker {

    private static final String PROJECTION_NAME = "credit-account-summary";

    private final OutboxEventRepository outbox;
    private final CreditAccountSummaryRepository summaries;
    private final CreditAccountSummaryProjector projector;
    private final TransactionRunner transactionRunner;
    private final ProjectionGating gating;
    private final ProjectionCheckpointRepository checkpointRepo;

    public int processOnce(int batchSize) {
        List<OutboxEvent> pending = outbox.findPending(batchSize);
        int processed = 0;
        for (OutboxEvent event : pending) {
            try {
                transactionRunner.runInTransaction(() -> {
                    processOne(event);
                    return null;
                });
                processed++;
            } catch (RuntimeException e) {
                outbox.markFailed(event.eventId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return processed;
    }

    public void processOne(OutboxEvent event) {
        String aggregateId = event.aggregateId();
        String aggregateType = event.aggregateType();

        Optional<ProjectionCheckpoint> checkpoint = checkpointRepo.find(PROJECTION_NAME, aggregateType, aggregateId);
        ProjectionGatingResult result = gating.decide(PROJECTION_NAME, event, checkpoint);

        if (result.decision() != ProjectionGatingResult.Decision.APPLY) {
            return;
        }

        CreditAccountId id = CreditAccountId.of(UUID.fromString(aggregateId));
        Optional<CreditAccountSummary> current = summaries.findById(id);
        CreditAccountSummary base = current.orElseGet(() -> projector.emptySummary(event));
        CreditAccountSummary next = projector.apply(event, base);

        CreditAccountSummary withEventId = new CreditAccountSummary(
                next.creditAccountId(),
                next.opened(),
                next.creditLimit(),
                next.outstandingBalance(),
                next.authorizedAmount(),
                next.availableLimit(),
                next.authorizations(),
                next.projectedVersion(),
                event.eventId(),
                next.updatedAt());
        summaries.upsert(withEventId);

        checkpointRepo.upsert(new ProjectionCheckpoint(
                PROJECTION_NAME,
                aggregateType,
                aggregateId,
                event.aggregateVersion(),
                event.eventId(),
                event.occurredAt()));

        outbox.markProcessed(event.eventId());
    }
}
