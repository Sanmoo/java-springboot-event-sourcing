package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectionWorker {

    private final OutboxEventRepository outbox;
    private final CreditAccountSummaryRepository summaries;
    private final CreditAccountSummaryProjector projector;
    private final ProjectionProperties properties;

    public int processOnce() {
        List<OutboxEvent> pending = outbox.findPending(properties.getBatchSize());
        int processed = 0;
        for (OutboxEvent event : pending) {
            try {
                processOne(event);
                processed++;
            } catch (RuntimeException e) {
                outbox.markFailed(event.eventId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return processed;
    }

    @Transactional
    public void processOne(OutboxEvent event) {
        CreditAccountId id = CreditAccountId.of(UUID.fromString(event.aggregateId()));
        Optional<CreditAccountSummary> current = summaries.findById(id);
        ProjectionTick tick = projector.project(event, current);
        if (!tick.applied() || tick.summary() == null) {
            return;
        }
        CreditAccountSummary withEventId = new CreditAccountSummary(
                tick.summary().creditAccountId(),
                tick.summary().opened(),
                tick.summary().creditLimit(),
                tick.summary().outstandingBalance(),
                tick.summary().authorizedAmount(),
                tick.summary().availableLimit(),
                tick.summary().authorizations(),
                tick.summary().projectedVersion(),
                event.eventId(),
                tick.summary().updatedAt());
        summaries.upsert(withEventId);
        outbox.markProcessed(event.eventId());
    }
}
