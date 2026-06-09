package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorized;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCaptured;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReceivePaymentUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private ReceivePaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new ReceivePaymentUseCase(support);
    }

    @Test
    void executeReceivesPayment() {
        UUID accountId = UUID.randomUUID();
        UUID authId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        AuthorizationId authorizationId = AuthorizationId.of(authId);
        Instant now = Instant.now();

        doNothing().when(idempotencyPort).lockKey(anyString());
        when(idempotencyPort.findByKey(anyString())).thenReturn(java.util.Optional.empty());
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(creditAccountId, now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2,
                        new CreditLimitAssigned(creditAccountId, Money.of("500.00"), now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3,
                        new PurchaseAuthorized(creditAccountId, authorizationId, Money.of("100.00"), "Store", now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 4,
                        new PurchaseCaptured(creditAccountId, authorizationId, Money.of("100.00"), now), now, Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(5L));

        var input = new ReceivePaymentInput("key-1", creditAccountId, Money.of("50.00"));
        var output = useCase.execute(input);

        assertThat(output.account().outstandingBalance()).isEqualTo("50.00");
        assertThat(output.replayed()).isFalse();
    }
}
