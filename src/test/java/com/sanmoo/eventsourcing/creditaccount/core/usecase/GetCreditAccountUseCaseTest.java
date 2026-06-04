package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PaymentReceived;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorizationReleased;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetCreditAccountUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private GetCreditAccountUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new GetCreditAccountUseCase(support);
    }

    @Test
    void executeReturnsAccountState() {
        UUID accountId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        Instant now = Instant.now();

        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(creditAccountId, now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2,
                        new CreditLimitAssigned(creditAccountId, Money.of("500.00"), now), now, Map.of())
        ));

        var input = new GetCreditAccountInput(creditAccountId);
        var output = useCase.execute(input);

        assertThat(output.account().creditAccountId()).isEqualTo(accountId.toString());
        assertThat(output.account().opened()).isTrue();
        assertThat(output.account().creditLimit()).isEqualTo("500.00");
    }

    @Test
    void executeThrowsWhenAccountDoesNotExist() {
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000404");
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);

        when(eventStore.loadEvents(eq("CreditAccount"), eq(accountId.toString())))
                .thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountInput(creditAccountId)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Credit account not found");
    }

    @Test
    void executeReturnsBalancesAndAuthorizationDetails() {
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000777");
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        AuthorizationId openAuthorizationId = AuthorizationId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        AuthorizationId capturedAuthorizationId = AuthorizationId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        AuthorizationId releasedAuthorizationId = AuthorizationId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));

        when(eventStore.loadEvents(eq("CreditAccount"), eq(accountId.toString())))
                .thenReturn(List.of(
                        envelope(1, new CreditAccountOpened(creditAccountId, Instant.parse("2026-06-01T10:00:00Z")), accountId),
                        envelope(2, new CreditLimitAssigned(creditAccountId, Money.of("500.00"), Instant.parse("2026-06-01T10:01:00Z")), accountId),
                        envelope(3, new PurchaseAuthorized(creditAccountId, openAuthorizationId, Money.of("50.00"), "Store A", Instant.parse("2026-06-01T10:02:00Z")), accountId),
                        envelope(4, new PurchaseAuthorized(creditAccountId, capturedAuthorizationId, Money.of("75.00"), "Store B", Instant.parse("2026-06-01T10:03:00Z")), accountId),
                        envelope(5, new PurchaseCaptured(creditAccountId, capturedAuthorizationId, Money.of("75.00"), Instant.parse("2026-06-01T10:04:00Z")), accountId),
                        envelope(6, new PaymentReceived(creditAccountId, Money.of("25.00"), Instant.parse("2026-06-01T10:05:00Z")), accountId),
                        envelope(7, new PurchaseAuthorized(creditAccountId, releasedAuthorizationId, Money.of("30.00"), "Store C", Instant.parse("2026-06-01T10:06:00Z")), accountId),
                        envelope(8, new PurchaseAuthorizationReleased(creditAccountId, releasedAuthorizationId, Money.of("30.00"), Instant.parse("2026-06-01T10:07:00Z")), accountId)
                ));

        CreditAccountOutput account = useCase.execute(new GetCreditAccountInput(creditAccountId)).account();

        assertThat(account.creditAccountId()).isEqualTo(accountId.toString());
        assertThat(account.opened()).isTrue();
        assertThat(account.creditLimit()).isEqualTo("500.00");
        assertThat(account.outstandingBalance()).isEqualTo("50.00");
        assertThat(account.authorizedAmount()).isEqualTo("50.00");
        assertThat(account.availableLimit()).isEqualTo("400.00");
        assertThat(account.authorizations())
                .containsExactlyInAnyOrder(
                        new PurchaseAuthorizationOutput(openAuthorizationId.value().toString(), "50.00", "OPEN", "Store A"),
                        new PurchaseAuthorizationOutput(capturedAuthorizationId.value().toString(), "75.00", "CAPTURED", "Store B"),
                        new PurchaseAuthorizationOutput(releasedAuthorizationId.value().toString(), "30.00", "RELEASED", "Store C")
                );
    }

    private static EventEnvelope envelope(long version, CreditAccountEvent event, UUID accountId) {
        return new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), version,
                event, Instant.parse("2026-06-01T10:00:00Z"), Map.of());
    }
}
