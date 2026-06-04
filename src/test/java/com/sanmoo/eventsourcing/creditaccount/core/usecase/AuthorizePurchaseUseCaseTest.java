package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
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

class AuthorizePurchaseUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private AuthorizePurchaseUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new AuthorizePurchaseUseCase(support);
    }

    @Test
    void executeAuthorizesPurchase() {
        UUID accountId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        Instant now = Instant.now();

        when(idempotencyPort.start(any(), eq("AuthorizePurchase"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(creditAccountId, now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2,
                        new CreditLimitAssigned(creditAccountId, Money.of("500.00"), now), now, Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(3L));

        AuthorizationId authorizationId = AuthorizationId.of(UUID.fromString("018f5f4b-6a3c-7000-8000-000000000123"));

        var input = new AuthorizePurchaseInput("key-1", creditAccountId, authorizationId, Money.of("100.00"), "Store");
        var output = useCase.execute(input);

        assertThat(output.account().authorizedAmount()).isEqualTo("100.00");
        assertThat(output.authorizationId()).isEqualTo(authorizationId.value().toString());
        assertThat(output.account().authorizations())
                .extracting(PurchaseAuthorizationOutput::authorizationId)
                .containsExactly(authorizationId.value().toString());
        assertThat(output.replayed()).isFalse();
    }

    @Test
    void executeReplayReturnsSuppliedAuthorizationId() throws Exception {
        UUID accountId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        AuthorizationId authorizationId = AuthorizationId.of(UUID.fromString("018f5f4b-6a3c-7000-8000-000000000099"));
        String responsePayload = objectMapper.writeValueAsString(Map.of(
                "aggregateId", accountId.toString(),
                "aggregateVersion", 3,
                "responseData", Map.of(
                        "creditAccountId", accountId.toString(),
                        "opened", true,
                        "creditLimit", "500.00",
                        "outstandingBalance", "0.00",
                        "authorizedAmount", "100.00",
                        "availableLimit", "400.00",
                        "authorizations", List.of(Map.of(
                                "authorizationId", authorizationId.value().toString(),
                                "amount", "100.00",
                                "status", "OPEN",
                                "merchantName", "Store"
                        ))
                )
        ));

        when(idempotencyPort.start(any(), eq("AuthorizePurchase"), any(), any()))
                .thenReturn(new IdempotencyDecision.Replay(responsePayload));

        var input = new AuthorizePurchaseInput("key-1", creditAccountId, authorizationId, Money.of("100.00"), "Store");
        var output = useCase.execute(input);

        assertThat(output.authorizationId()).isEqualTo(authorizationId.value().toString());
        assertThat(output.replayed()).isTrue();
        verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
    }

}
