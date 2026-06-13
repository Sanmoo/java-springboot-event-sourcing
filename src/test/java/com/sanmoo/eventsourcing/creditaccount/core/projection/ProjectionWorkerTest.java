package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.ProjectionCheckpointRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.ProjectionCheckpoint;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectionWorkerTest {

    @Test
    void processOnce_appliesPendingEvents() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        CreditAccountSummaryRepository summaries = mock(CreditAccountSummaryRepository.class);
        CreditAccountSummaryProjector projector = new CreditAccountSummaryProjector();
        TransactionRunner transactionRunner = new TransactionRunner() {
            @Override
            public <T> T runInTransaction(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
        ProjectionGating gating = new ProjectionGating();
        ProjectionCheckpointRepository checkpointRepo = mock(ProjectionCheckpointRepository.class);

        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, "CreditAccount", accountId.toString(), 1L,
                "CreditAccountOpened", new CreditAccountOpened(CreditAccountId.of(accountId), Instant.now()),
                java.util.Map.of(), Instant.now());

        when(outbox.findPending(10)).thenReturn(List.of(event));
        when(summaries.findById(CreditAccountId.of(accountId))).thenReturn(Optional.empty());
        when(checkpointRepo.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        ProjectionWorker worker = new ProjectionWorker(outbox, summaries, projector, transactionRunner, gating, checkpointRepo);
        int processed = worker.processOnce(10);

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<CreditAccountSummary> captor = ArgumentCaptor.forClass(CreditAccountSummary.class);
        verify(summaries).upsert(captor.capture());
        assertThat(captor.getValue().creditAccountId()).isEqualTo(accountId);
        verify(checkpointRepo).upsert(any(ProjectionCheckpoint.class));
        verify(outbox).markProcessed(eventId);
    }

    @Test
    void processOnce_recordsFailureOnException() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        CreditAccountSummaryRepository summaries = mock(CreditAccountSummaryRepository.class);
        CreditAccountSummaryProjector projector = mock(CreditAccountSummaryProjector.class);
        TransactionRunner transactionRunner = new TransactionRunner() {
            @Override
            public <T> T runInTransaction(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
        ProjectionGating gating = mock(ProjectionGating.class);
        ProjectionCheckpointRepository checkpointRepo = mock(ProjectionCheckpointRepository.class);

        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, "CreditAccount", accountId.toString(), 1L,
                "CreditAccountOpened", new CreditAccountOpened(CreditAccountId.of(accountId), Instant.now()),
                java.util.Map.of(), Instant.now());

        when(outbox.findPending(10)).thenReturn(List.of(event));
        when(summaries.findById(any())).thenReturn(Optional.empty());
        when(checkpointRepo.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(gating.decide(anyString(), any(), any())).thenThrow(new RuntimeException("boom"));

        ProjectionWorker worker = new ProjectionWorker(outbox, summaries, projector, transactionRunner, gating, checkpointRepo);
        int processed = worker.processOnce(10);

        assertThat(processed).isEqualTo(0);
        verify(outbox).markFailed(eq(eventId), contains("boom"));
    }
}
