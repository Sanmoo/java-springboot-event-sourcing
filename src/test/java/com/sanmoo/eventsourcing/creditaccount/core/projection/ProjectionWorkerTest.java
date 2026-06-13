package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxDeliveryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventLoader;
import com.sanmoo.eventsourcing.creditaccount.core.port.ProjectionCheckpointRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxDelivery;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.ProjectionCheckpoint;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectionWorkerTest {

    @Test
    void processOnce_appliesPendingEvent() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 1L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(checkpoints.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(summaries.findById(any())).thenReturn(Optional.empty());

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.processed()).isEqualTo(1);
        verify(deliveries).markProcessed(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName());
        verify(checkpoints).upsert(any(ProjectionCheckpoint.class));
        verify(summaries).upsert(any(CreditAccountSummary.class));
    }

    @Test
    void processOnce_blocksOnGap() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 3L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(checkpoints.find(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new ProjectionCheckpoint(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                        "CreditAccount", aggregateId.toString(), 1L, UUID.randomUUID(), Instant.now())));

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.blocked()).isEqualTo(1);
        verify(deliveries).markBlocked(eq(eventId), eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString());
        verify(deliveries, never()).markProcessed(any(), anyString());
    }

    private TransactionRunner mockTx() {
        return new TransactionRunner() {
            @Override
            public <T> T runInTransaction(Supplier<T> action) {
                return action.get();
            }
        };
    }
}
