package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AssignCreditLimitInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AssignCreditLimitOutput;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStore;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
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

    private EventStore eventStore;
    private IdempotencyRepository idempotencyRepository;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private AssignCreditLimitUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStore.class);
        idempotencyRepository = mock(IdempotencyRepository.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyRepository, objectMapper);
        useCase = new AssignCreditLimitUseCase(support);
    }

    @Test
    void executeAssignsCreditLimitToExistingAccount() {
        UUID accountId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);

        doNothing().when(idempotencyRepository).lockKey(anyString());
        when(idempotencyRepository.findByKey(anyString())).thenReturn(java.util.Optional.empty());
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

        doNothing().when(idempotencyRepository).lockKey(eq("conflict-key"));
        when(idempotencyRepository.findByKey(eq("conflict-key"))).thenReturn(java.util.Optional.of(
                new IdempotencyRecord(
                        "conflict-key",
                        "AssignCreditLimit",
                        creditAccountId.value().toString(),
                        "different-request-hash",
                        "{\"aggregateId\":\"%s\",\"aggregateVersion\":1,\"responseData\":{}}".formatted(creditAccountId.value()),
                        1L
                )
        ));

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("idempotency key reused");

        verify(eventStore, never()).loadEvents(any(), any());
        verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
        verify(idempotencyRepository, never()).saveResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void successfulCommandCompletesIdempotencyWithSerializedResult() throws Exception {
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000250");
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        var input = new AssignCreditLimitInput("serialize-key", creditAccountId, Money.of("250.00"));

        doNothing().when(idempotencyRepository).lockKey(eq("serialize-key"));
        when(idempotencyRepository.findByKey(eq("serialize-key"))).thenReturn(java.util.Optional.empty());
        when(eventStore.loadEvents(eq("CreditAccount"), eq(accountId.toString()))).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(creditAccountId, Instant.parse("2026-06-01T10:00:00Z")),
                        Instant.parse("2026-06-01T10:00:00Z"), Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(2L));

        AssignCreditLimitOutput output = useCase.execute(input);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                eq(accountId.toString()),
                eq(1L),
                anyList(),
                metadataCaptor.capture()
        );
        assertThat(metadataCaptor.getValue())
                .containsKeys("idempotencyKey", "commandType", "requestHash")
                .containsEntry("idempotencyKey", "serialize-key")
                .containsEntry("commandType", "AssignCreditLimit");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyRepository).saveResult(
                eq("serialize-key"),
                eq("AssignCreditLimit"),
                eq(accountId.toString()),
                anyString(),
                payloadCaptor.capture(),
                eq(2L)
        );

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
