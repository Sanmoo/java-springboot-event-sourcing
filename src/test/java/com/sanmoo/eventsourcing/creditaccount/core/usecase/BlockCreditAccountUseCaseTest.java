package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStore;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRepository;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.BlockCreditAccountInput;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockCreditAccountUseCaseTest {
    @Test
    void executeRecordsBlockingEventAndReturnsBlockedState() {
        EventStore events = mock(EventStore.class);
        IdempotencyRepository idempotency = mock(IdempotencyRepository.class);
        CreditAccountUseCaseSupport support = new CreditAccountUseCaseSupport(events, idempotency, new ObjectMapper());
        BlockCreditAccountUseCase useCase = new BlockCreditAccountUseCase(support);
        UUID id = UUID.randomUUID();
        CreditAccountId accountId = CreditAccountId.of(id);
        Instant openedAt = Instant.parse("2026-06-01T10:00:00Z");
        when(idempotency.findByKey(anyString())).thenReturn(java.util.Optional.empty());
        when(events.loadEvents("CreditAccount", id.toString())).thenReturn(List.of(new EventEnvelope(
                UUID.randomUUID(), "CreditAccount", id.toString(), 1L,
                new CreditAccountOpened(accountId, openedAt), openedAt, Map.of())));
        when(events.appendEvents(anyString(), anyString(), anyLong(), anyList(), anyMap())).thenReturn(new AppendResult(2L));

        var output = useCase.execute(new BlockCreditAccountInput("block-key", accountId, "review"));

        assertThat(output.account().status()).isEqualTo("BLOCKED");
        assertThat(output.replayed()).isFalse();
        verify(idempotency).saveResult(eq("block-key"), eq("BlockCreditAccount"), eq(id.toString()), anyString(), anyString());
    }
}
