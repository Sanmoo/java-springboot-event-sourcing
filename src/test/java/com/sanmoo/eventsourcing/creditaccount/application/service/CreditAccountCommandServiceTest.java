package com.sanmoo.eventsourcing.creditaccount.application.service;

import tools.jackson.databind.ObjectMapper;
import com.sanmoo.eventsourcing.creditaccount.application.command.AssignCreditLimitCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.AuthorizePurchaseCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.CapturePurchaseCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.ChangeCreditLimitCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.OpenCreditAccountCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.ReceivePaymentCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.ReleasePurchaseAuthorizationCommand;
import com.sanmoo.eventsourcing.creditaccount.application.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.application.result.CommandResult;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitChanged;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PaymentReceived;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorized;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorizationReleased;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCaptured;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreditAccountCommandServiceTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private CreditAccountCommandService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        service = new CreditAccountCommandService(eventStore, idempotencyPort, objectMapper);
    }

    private EventEnvelope envelope(long version, CreditAccountEvent event, String aggregateId) {
        return new EventEnvelope(
                UUID.randomUUID(), "CreditAccount", aggregateId, version, event,
                Instant.now(), Map.of()
        );
    }

    @Test
    void openCreditAccountAppendsCreditAccountOpenedAtExpectedVersionZero() throws Exception {
        // Arrange
        OpenCreditAccountCommand command = new OpenCreditAccountCommand("test-key-1");
        when(idempotencyPort.start(eq("test-key-1"), eq("OpenCreditAccount"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("test-key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of());
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(1L));

        // Act
        CommandResult result = service.openCreditAccount(command);

        // Assert
        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                anyString(),
                eq(0L),
                argThat(events -> {
                    if (events.size() != 1) return false;
                    return events.get(0) instanceof CreditAccountOpened;
                }),
                anyMap()
        );
        verify(idempotencyPort).complete(eq("test-key-1"), anyString());

        assertThat(result.aggregateId()).isNotNull();
        assertThat(result.aggregateVersion()).isEqualTo(1L);
        assertThat(result.responseData()).isNotNull();
    }

    @Test
    void assignCreditLimitAppendsEventAtExpectedVersion() throws Exception {
        // Arrange
        CreditAccountId accountId = CreditAccountId.newId();
        String aggregateId = accountId.value().toString();
        AssignCreditLimitCommand command = new AssignCreditLimitCommand(
                "test-key-assign", accountId, Money.of("1000.00"));

        when(idempotencyPort.start(eq("test-key-assign"), eq("AssignCreditLimit"), eq(aggregateId), any()))
                .thenReturn(new IdempotencyDecision.Started("test-key-assign"));

        // History: account was opened (version 1)
        when(eventStore.loadEvents(eq("CreditAccount"), eq(aggregateId)))
                .thenReturn(List.of(
                        envelope(1L, new CreditAccountOpened(accountId, Instant.now()), aggregateId)
                ));

        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(2L));

        // Act
        CommandResult result = service.assignCreditLimit(command);

        // Assert
        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                eq(aggregateId),
                eq(1L),
                argThat(events -> {
                    if (events.size() != 1) return false;
                    return events.get(0) instanceof CreditLimitAssigned;
                }),
                anyMap()
        );
        verify(idempotencyPort).complete(eq("test-key-assign"), anyString());

        assertThat(result.aggregateId()).isEqualTo(aggregateId);
        assertThat(result.aggregateVersion()).isEqualTo(2L);
        assertThat(result.responseData()).isNotNull();
    }

    @Test
    void authorizePurchaseAppendsEventAtExpectedVersion() throws Exception {
        // Arrange
        CreditAccountId accountId = CreditAccountId.newId();
        String aggregateId = accountId.value().toString();
        AuthorizationId authorizationId = AuthorizationId.newId();
        AuthorizePurchaseCommand command = new AuthorizePurchaseCommand(
                "test-key-auth", accountId, authorizationId, Money.of("500.00"), "Test Merchant");

        when(idempotencyPort.start(eq("test-key-auth"), eq("AuthorizePurchase"), eq(aggregateId), any()))
                .thenReturn(new IdempotencyDecision.Started("test-key-auth"));

        // History: account opened (v1) + credit limit assigned $1000 (v2)
        when(eventStore.loadEvents(eq("CreditAccount"), eq(aggregateId)))
                .thenReturn(List.of(
                        envelope(1L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                        envelope(2L, new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.now()), aggregateId)
                ));

        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(3L));

        // Act
        CommandResult result = service.authorizePurchase(command);

        // Assert
        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                eq(aggregateId),
                eq(2L),
                argThat(events -> {
                    if (events.size() != 1) return false;
                    return events.get(0) instanceof PurchaseAuthorized;
                }),
                anyMap()
        );
        verify(idempotencyPort).complete(eq("test-key-auth"), anyString());

        assertThat(result.aggregateId()).isEqualTo(aggregateId);
        assertThat(result.aggregateVersion()).isEqualTo(3L);
        assertThat(result.responseData()).isNotNull();
    }

    @Test
    void capturePurchaseAppendsEventAtExpectedVersion() throws Exception {
        // Arrange
        CreditAccountId accountId = CreditAccountId.newId();
        String aggregateId = accountId.value().toString();
        AuthorizationId authorizationId = AuthorizationId.newId();
        CapturePurchaseCommand command = new CapturePurchaseCommand(
                "test-key-capture", accountId, authorizationId);

        when(idempotencyPort.start(eq("test-key-capture"), eq("CapturePurchase"), eq(aggregateId), any()))
                .thenReturn(new IdempotencyDecision.Started("test-key-capture"));

        // History: opened (v1) + limit assigned $1000 (v2) + purchase authorized $500 (v3)
        when(eventStore.loadEvents(eq("CreditAccount"), eq(aggregateId)))
                .thenReturn(List.of(
                        envelope(1L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                        envelope(2L, new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.now()), aggregateId),
                        envelope(3L, new PurchaseAuthorized(accountId, authorizationId, Money.of("500.00"), "Test Merchant", Instant.now()), aggregateId)
                ));

        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(4L));

        // Act
        CommandResult result = service.capturePurchase(command);

        // Assert
        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                eq(aggregateId),
                eq(3L),
                argThat(events -> {
                    if (events.size() != 1) return false;
                    return events.get(0) instanceof PurchaseCaptured;
                }),
                anyMap()
        );
        verify(idempotencyPort).complete(eq("test-key-capture"), anyString());

        assertThat(result.aggregateId()).isEqualTo(aggregateId);
        assertThat(result.aggregateVersion()).isEqualTo(4L);
        assertThat(result.responseData()).isNotNull();
    }

    @Test
    void releasePurchaseAuthorizationAppendsEventAtExpectedVersion() throws Exception {
        // Arrange
        CreditAccountId accountId = CreditAccountId.newId();
        String aggregateId = accountId.value().toString();
        AuthorizationId authorizationId = AuthorizationId.newId();
        ReleasePurchaseAuthorizationCommand command = new ReleasePurchaseAuthorizationCommand(
                "test-key-release", accountId, authorizationId);

        when(idempotencyPort.start(eq("test-key-release"), eq("ReleasePurchaseAuthorization"), eq(aggregateId), any()))
                .thenReturn(new IdempotencyDecision.Started("test-key-release"));

        // History: opened (v1) + limit assigned $1000 (v2) + purchase authorized $500 (v3)
        when(eventStore.loadEvents(eq("CreditAccount"), eq(aggregateId)))
                .thenReturn(List.of(
                        envelope(1L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                        envelope(2L, new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.now()), aggregateId),
                        envelope(3L, new PurchaseAuthorized(accountId, authorizationId, Money.of("500.00"), "Test Merchant", Instant.now()), aggregateId)
                ));

        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(4L));

        // Act
        CommandResult result = service.releasePurchaseAuthorization(command);

        // Assert
        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                eq(aggregateId),
                eq(3L),
                argThat(events -> {
                    if (events.size() != 1) return false;
                    return events.get(0) instanceof PurchaseAuthorizationReleased;
                }),
                anyMap()
        );
        verify(idempotencyPort).complete(eq("test-key-release"), anyString());

        assertThat(result.aggregateId()).isEqualTo(aggregateId);
        assertThat(result.aggregateVersion()).isEqualTo(4L);
        assertThat(result.responseData()).isNotNull();
    }

    @Test
    void receivePaymentAppendsEventAtExpectedVersion() throws Exception {
        // Arrange
        CreditAccountId accountId = CreditAccountId.newId();
        String aggregateId = accountId.value().toString();
        AuthorizationId authorizationId = AuthorizationId.newId();
        ReceivePaymentCommand command = new ReceivePaymentCommand(
                "test-key-payment", accountId, Money.of("200.00"));

        when(idempotencyPort.start(eq("test-key-payment"), eq("ReceivePayment"), eq(aggregateId), any()))
                .thenReturn(new IdempotencyDecision.Started("test-key-payment"));

        // History: opened (v1) + limit assigned $1000 (v2) + purchase authorized $500 (v3)
        //          + purchase captured $500 (v4) → outstanding balance = $500
        when(eventStore.loadEvents(eq("CreditAccount"), eq(aggregateId)))
                .thenReturn(List.of(
                        envelope(1L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                        envelope(2L, new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.now()), aggregateId),
                        envelope(3L, new PurchaseAuthorized(accountId, authorizationId, Money.of("500.00"), "Test Merchant", Instant.now()), aggregateId),
                        envelope(4L, new PurchaseCaptured(accountId, authorizationId, Money.of("500.00"), Instant.now()), aggregateId)
                ));

        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(5L));

        // Act
        CommandResult result = service.receivePayment(command);

        // Assert
        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                eq(aggregateId),
                eq(4L),
                argThat(events -> {
                    if (events.size() != 1) return false;
                    return events.get(0) instanceof PaymentReceived;
                }),
                anyMap()
        );
        verify(idempotencyPort).complete(eq("test-key-payment"), anyString());

        assertThat(result.aggregateId()).isEqualTo(aggregateId);
        assertThat(result.aggregateVersion()).isEqualTo(5L);
        assertThat(result.responseData()).isNotNull();
    }

    @Test
    void changeCreditLimitAppendsEventAtExpectedVersion() throws Exception {
        // Arrange
        CreditAccountId accountId = CreditAccountId.newId();
        String aggregateId = accountId.value().toString();
        ChangeCreditLimitCommand command = new ChangeCreditLimitCommand(
                "test-key-change", accountId, Money.of("2000.00"));

        when(idempotencyPort.start(eq("test-key-change"), eq("ChangeCreditLimit"), eq(aggregateId), any()))
                .thenReturn(new IdempotencyDecision.Started("test-key-change"));

        // History: account opened (v1) + credit limit assigned $1000 (v2)
        when(eventStore.loadEvents(eq("CreditAccount"), eq(aggregateId)))
                .thenReturn(List.of(
                        envelope(1L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                        envelope(2L, new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.now()), aggregateId)
                ));

        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(3L));

        // Act
        CommandResult result = service.changeCreditLimit(command);

        // Assert
        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                eq(aggregateId),
                eq(2L),
                argThat(events -> {
                    if (events.size() != 1) return false;
                    return events.get(0) instanceof CreditLimitChanged;
                }),
                anyMap()
        );
        verify(idempotencyPort).complete(eq("test-key-change"), anyString());

        assertThat(result.aggregateId()).isEqualTo(aggregateId);
        assertThat(result.aggregateVersion()).isEqualTo(3L);
        assertThat(result.responseData()).isNotNull();
    }

    @Test
    void commandReplayReturnsPreviousResultWithoutAppending() throws Exception {
        // Arrange
        CreditAccountId accountId = CreditAccountId.newId();
        String aggregateId = accountId.value().toString();
        AssignCreditLimitCommand command = new AssignCreditLimitCommand(
                "test-key-replay", accountId, Money.of("1000.00"));

        // Simulate a stored response from a previous successful execution
        Map<String, Object> previousResponseData = Map.of(
                "creditAccountId", aggregateId,
                "opened", true,
                "creditLimit", "1000.00",
                "outstandingBalance", "0.00",
                "authorizedAmount", "0.00",
                "availableLimit", "1000.00",
                "authorizations", List.of()
        );
        CommandResult previousResult = new CommandResult(
                aggregateId,
                2L,
                previousResponseData
        );
        String storedPayload = objectMapper.writeValueAsString(previousResult);

        when(idempotencyPort.start(eq("test-key-replay"), eq("AssignCreditLimit"), eq(aggregateId), any()))
                .thenReturn(new IdempotencyDecision.Replay(storedPayload));

        // Act
        CommandResult result = service.assignCreditLimit(command);

        // Assert
        verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
        verify(idempotencyPort, never()).complete(anyString(), anyString());

        assertThat(result.aggregateId()).isEqualTo(aggregateId);
        assertThat(result.aggregateVersion()).isEqualTo(2L);
        assertThat(result.replayed()).isTrue();
        assertThat(result.responseData())
                .containsEntry("creditAccountId", aggregateId)
                .containsEntry("creditLimit", "1000.00");
    }

    @Test
    void sameIdempotencyKeyAndHashReturnsPreviousResponseWithoutAppending() throws Exception {
        // Arrange
        OpenCreditAccountCommand command = new OpenCreditAccountCommand("test-key-2");

        // Simulate a stored response from a previous successful execution
        Map<String, Object> previousResponseData = Map.of(
                "creditAccountId", "550e8400-e29b-41d4-a716-446655440000",
                "opened", true
        );
        CommandResult previousResult = new CommandResult(
                "550e8400-e29b-41d4-a716-446655440000",
                1L,
                previousResponseData
        );
        String storedPayload = objectMapper.writeValueAsString(previousResult);

        when(idempotencyPort.start(eq("test-key-2"), eq("OpenCreditAccount"), any(), any()))
                .thenReturn(new IdempotencyDecision.Replay(storedPayload));

        // Act
        CommandResult result = service.openCreditAccount(command);

        // Assert
        verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
        verify(idempotencyPort, never()).complete(anyString(), anyString());

        assertThat(result.aggregateId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(result.aggregateVersion()).isEqualTo(1L);
        assertThat(result.responseData())
                .containsEntry("creditAccountId", "550e8400-e29b-41d4-a716-446655440000")
                .containsEntry("opened", true);
    }
}
