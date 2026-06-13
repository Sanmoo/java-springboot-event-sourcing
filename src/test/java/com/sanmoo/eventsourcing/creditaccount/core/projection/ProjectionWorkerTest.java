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

    @Test
    void processOnce_marksPermanentFailureOnInvalidVersion() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 0L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.failed()).isEqualTo(1);
        verify(deliveries).markPermanentFailure(eq(eventId), eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()),
                eq(1), anyString());
    }

    @Test
    void processOnce_marksAlreadyApplied() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 2L,
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
                .thenReturn(Optional.of(new ProjectionCheckpoint(
                        ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                        "CreditAccount", aggregateId.toString(), 5L, UUID.randomUUID(), Instant.now())));

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.processed()).isEqualTo(1);
        verify(deliveries).markProcessed(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName());
    }

    @Test
    void processOnce_retriesOnTransientFailure() {
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
        when(summaries.findById(any())).thenThrow(new RuntimeException("boom"));

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.retried()).isEqualTo(1);
        verify(deliveries).markRetryableFailure(eq(eventId), eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()),
                eq(1), eq(10), anyString(), any());
    }

    @Test
    void processOnce_marksPermanentFailureOnMaxAttempts() {
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
                OutboxDeliveryStatus.PROCESSING, 9, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(checkpoints.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(summaries.findById(any())).thenThrow(new RuntimeException("boom"));

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.failed()).isEqualTo(1);
        verify(deliveries).markPermanentFailure(eq(eventId), eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()),
                eq(10), anyString());
    }

    @Test
    void processOnce_emptyClaimReturnsEmptyResult() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of());

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.claimed()).isZero();
        assertThat(result.processed()).isZero();
        assertThat(result.blocked()).isZero();
        assertThat(result.retried()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void processOnce_applyUnblocksNextVersion() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var opened = new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now());
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 1L,
                "CreditAccountOpened", opened, java.util.Map.of(), Instant.now());
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());
        var existing = new CreditAccountSummary(aggregateId, false, null,
                "0.00", "0.00", "0.00", List.of(), 0L, null, Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(checkpoints.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(summaries.findById(any())).thenReturn(Optional.of(existing));

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.processed()).isEqualTo(1);
        verify(summaries).upsert(argThat(s -> s.opened() && s.projectedVersion() == 1L));
        verify(checkpoints).upsert(argThat(cp -> cp.lastProjectedVersion() == 1L && cp.lastEventId().equals(eventId)));
        verify(deliveries).unblockNextVersion(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()),
                eq("CreditAccount"), eq(aggregateId.toString()), eq(2L));
    }

    @Test
    void processOnce_retriesWhenEventNotFound() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.empty());

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.retried()).isEqualTo(1);
        verify(deliveries).markRetryableFailure(eq(eventId), eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()),
                eq(1), eq(10), argThat(err -> err != null && err.contains("IllegalStateException")), any());
    }

    @Test
    void processOnce_nullClaimReturnsEmptyResult() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(null);

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.claimed()).isZero();
        assertThat(result.processed()).isZero();
        assertThat(result.blocked()).isZero();
        assertThat(result.retried()).isZero();
        assertThat(result.failed()).isZero();
        verify(deliveries, never()).markProcessed(any(), anyString());
        verify(deliveries, never()).markBlocked(any(), anyString(), anyString());
        verify(deliveries, never()).markRetryableFailure(any(), anyString(), anyInt(), anyInt(), anyString(), any());
        verify(deliveries, never()).markPermanentFailure(any(), anyString(), anyInt(), anyString());
    }

    @Test
    void processRetryBackoff_isCappedByMaxBackoff() {
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
                OutboxDeliveryStatus.PROCESSING, 8, 20, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(checkpoints.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(summaries.findById(any())).thenThrow(new RuntimeException("boom"));

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        props.setInitialBackoff(java.time.Duration.ofSeconds(10));
        props.setMaxBackoff(java.time.Duration.ofSeconds(30));
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        worker.processOnce(10);
        verify(deliveries).markRetryableFailure(eq(eventId), eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()),
                eq(9), eq(20), anyString(),
                argThat(backoff -> backoff != null && backoff.equals(java.time.Duration.ofSeconds(30))));
    }

    @Test
    void processOnce_drainLimitStopsAfterFirstEventPerAggregate() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        var event1 = new OutboxEvent(eventId1, "CreditAccount", aggregateId.toString(), 1L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var event2 = new OutboxEvent(eventId2, "CreditAccount", aggregateId.toString(), 2L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var delivery1 = new OutboxDelivery(eventId1, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());
        var delivery2 = new OutboxDelivery(eventId2, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of(delivery1, delivery2));
        when(eventLoader.findById(eventId1)).thenReturn(Optional.of(event1));
        when(eventLoader.findById(eventId2)).thenReturn(Optional.of(event2));
        when(checkpoints.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(summaries.findById(any())).thenReturn(Optional.empty());

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        props.setMaxConsecutiveEventsPerAggregate(1);
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.processed()).isEqualTo(1);
        verify(deliveries, times(1)).markProcessed(any(), anyString());
    }

    @Test
    void processOnce_processesClaimedDeliveriesInVersionOrder() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        var event1 = new OutboxEvent(eventId1, "CreditAccount", aggregateId.toString(), 1L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var event2 = new OutboxEvent(eventId2, "CreditAccount", aggregateId.toString(), 2L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var delivery1 = new OutboxDelivery(eventId1, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());
        var delivery2 = new OutboxDelivery(eventId2, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName()), anyString(), anyInt()))
                .thenReturn(List.of(delivery2, delivery1));
        when(eventLoader.findById(eventId1)).thenReturn(Optional.of(event1));
        when(eventLoader.findById(eventId2)).thenReturn(Optional.of(event2));
        when(checkpoints.find(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ProjectionCheckpoint(
                        ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName(),
                        "CreditAccount", aggregateId.toString(), 1L, eventId1, Instant.now())));
        when(summaries.findById(any())).thenReturn(Optional.empty());

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, mockTx());

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.processed()).isEqualTo(2);
        var inOrder = inOrder(deliveries);
        inOrder.verify(deliveries).markProcessed(eventId1, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName());
        inOrder.verify(deliveries).markProcessed(eventId2, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR.getName());
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
