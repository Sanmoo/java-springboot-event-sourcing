package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignCreditLimitUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private AssignCreditLimitUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new AssignCreditLimitUseCase(support);
    }

    @Test
    void executeAssignsCreditLimitToExistingAccount() {
        UUID accountId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);

        when(idempotencyPort.start(any(), eq("AssignCreditLimit"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(creditAccountId, Instant.now()), Instant.now(), Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(2L));

        var input = new AssignCreditLimitInput("key-1", creditAccountId, Money.of("500.00"));
        var output = useCase.execute(input);

        assertThat(output.account().creditLimit()).isEqualTo("500.00");
        assertThat(output.replayed()).isFalse();
    }

    @Test
    void conflictThrowsIdempotencyConflictExceptionWithoutSideEffects() {
        CreditAccountId creditAccountId = CreditAccountId.of(UUID.randomUUID());
        var input = new AssignCreditLimitInput("conflict-key", creditAccountId, Money.of("200.00"));

        when(idempotencyPort.start(eq("conflict-key"), eq("AssignCreditLimit"), eq(creditAccountId.value().toString()), any()))
                .thenReturn(new IdempotencyDecision.Conflict("idempotency key reused with different payload"));

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("idempotency key reused");

        verify(eventStore, never()).loadEvents(any(), any());
        verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
        verify(idempotencyPort, never()).complete(anyString(), anyString());
    }

    @Test
    void successfulCommandCompletesIdempotencyWithSerializedResult() throws Exception {
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000250");
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        var input = new AssignCreditLimitInput("serialize-key", creditAccountId, Money.of("250.00"));

        when(idempotencyPort.start(eq("serialize-key"), eq("AssignCreditLimit"), eq(accountId.toString()), any()))
                .thenReturn(new IdempotencyDecision.Started("serialize-key"));
        when(eventStore.loadEvents(eq("CreditAccount"), eq(accountId.toString()))).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(creditAccountId, Instant.parse("2026-06-01T10:00:00Z")),
                        Instant.parse("2026-06-01T10:00:00Z"), Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(2L));

        AssignCreditLimitOutput output = useCase.execute(input);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyPort).complete(eq("serialize-key"), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> stored = objectMapper.readValue(payloadCaptor.getValue(), Map.class);
        assertThat(stored)
                .containsEntry("aggregateId", output.account().creditAccountId())
                .containsEntry("aggregateVersion", 2)
                .containsKey("responseData");
        @SuppressWarnings("unchecked")
        Map<String, Object> responseData = (Map<String, Object>) stored.get("responseData");
        assertThat(responseData)
                .containsEntry("creditLimit", output.account().creditLimit())
                .containsEntry("creditLimit", "250.00");
        assertThat(output.replayed()).isFalse();
    }
}
