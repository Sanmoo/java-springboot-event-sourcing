package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.*;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreditAccountSummaryProjectorTest {

    private final CreditAccountSummaryProjector projector = new CreditAccountSummaryProjector();

    @Test
    void applies_creditAccountOpened_createsSummary() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = outbox(
                UUID.randomUUID(), id, 1L,
                new CreditAccountOpened(CreditAccountId.of(id), Instant.parse("2025-01-01T00:00:00Z")));

        ProjectionTick tick = projector.project(event, Optional.empty());

        CreditAccountSummary summary = tick.summary();
        assertThat(summary).isNotNull();
        assertThat(summary.creditAccountId()).isEqualTo(id);
        assertThat(summary.opened()).isTrue();
        assertThat(summary.outstandingBalance()).isEqualTo("0.00");
        assertThat(summary.authorizedAmount()).isEqualTo("0.00");
        assertThat(summary.availableLimit()).isEqualTo("0.00");
        assertThat(summary.projectedVersion()).isEqualTo(1L);
        assertThat(tick.applied()).isTrue();
    }

    @Test
    void applies_creditLimitAssigned_setsLimit() {
        UUID id = UUID.randomUUID();
        CreditAccountId accountId = CreditAccountId.of(id);
        OutboxEvent opened = outbox(
                UUID.randomUUID(), id, 1L,
                new CreditAccountOpened(accountId, Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(
                UUID.randomUUID(), id, 2L,
                new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.parse("2025-01-01T00:00:01Z")));

        CreditAccountSummary base = projector.project(opened, Optional.empty()).summary();
        ProjectionTick tick = projector.project(limit, Optional.of(base));

        assertThat(tick.summary().creditLimit()).isEqualTo("1000.00");
        assertThat(tick.summary().availableLimit()).isEqualTo("1000.00");
        assertThat(tick.summary().projectedVersion()).isEqualTo(2L);
    }

    @Test
    void applies_purchaseAuthorized_reducesAvailableAndAddsAuth() {
        UUID id = UUID.randomUUID();
        CreditAccountId accountId = CreditAccountId.of(id);
        AuthorizationId authId = AuthorizationId.of(UUID.randomUUID());
        OutboxEvent opened = outbox(
                UUID.randomUUID(), id, 1L,
                new CreditAccountOpened(accountId, Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(
                UUID.randomUUID(), id, 2L,
                new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.parse("2025-01-01T00:00:01Z")));
        OutboxEvent auth = outbox(
                UUID.randomUUID(), id, 3L,
                new PurchaseAuthorized(accountId, authId, Money.of("200.00"), "Store",
                        Instant.parse("2025-01-01T00:00:02Z")));

        CreditAccountSummary s1 = projector.project(opened, Optional.empty()).summary();
        CreditAccountSummary s2 = projector.project(limit, Optional.of(s1)).summary();
        ProjectionTick tick = projector.project(auth, Optional.of(s2));

        assertThat(tick.summary().authorizedAmount()).isEqualTo("200.00");
        assertThat(tick.summary().availableLimit()).isEqualTo("800.00");
        assertThat(tick.summary().authorizations()).hasSize(1);
        assertThat(tick.summary().authorizations().get(0).status()).isEqualTo("OPEN");
    }

    @Test
    void applies_capturePaymentRelease_payment() {
        UUID id = UUID.randomUUID();
        CreditAccountId accountId = CreditAccountId.of(id);
        AuthorizationId authId = AuthorizationId.of(UUID.randomUUID());
        OutboxEvent opened = outbox(
                UUID.randomUUID(), id, 1L,
                new CreditAccountOpened(accountId, Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(
                UUID.randomUUID(), id, 2L,
                new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.parse("2025-01-01T00:00:01Z")));
        OutboxEvent auth = outbox(
                UUID.randomUUID(), id, 3L,
                new PurchaseAuthorized(accountId, authId, Money.of("200.00"), "Store",
                        Instant.parse("2025-01-01T00:00:02Z")));
        OutboxEvent capture = outbox(
                UUID.randomUUID(), id, 4L,
                new PurchaseCaptured(accountId, authId, Money.of("200.00"),
                        Instant.parse("2025-01-01T00:00:03Z")));

        CreditAccountSummary s1 = projector.project(opened, Optional.empty()).summary();
        CreditAccountSummary s2 = projector.project(limit, Optional.of(s1)).summary();
        CreditAccountSummary s3 = projector.project(auth, Optional.of(s2)).summary();
        ProjectionTick tick = projector.project(capture, Optional.of(s3));

        assertThat(tick.summary().outstandingBalance()).isEqualTo("200.00");
        assertThat(tick.summary().authorizedAmount()).isEqualTo("0.00");
        assertThat(tick.summary().authorizations().get(0).status()).isEqualTo("CAPTURED");
    }

    @Test
    void applies_paymentReceived_reducesOutstanding() {
        UUID id = UUID.randomUUID();
        CreditAccountId accountId = CreditAccountId.of(id);
        AuthorizationId authId = AuthorizationId.of(UUID.randomUUID());
        OutboxEvent opened = outbox(
                UUID.randomUUID(), id, 1L,
                new CreditAccountOpened(accountId, Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(
                UUID.randomUUID(), id, 2L,
                new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.parse("2025-01-01T00:00:01Z")));
        OutboxEvent auth = outbox(
                UUID.randomUUID(), id, 3L,
                new PurchaseAuthorized(accountId, authId, Money.of("200.00"), "Store",
                        Instant.parse("2025-01-01T00:00:02Z")));
        OutboxEvent capture = outbox(
                UUID.randomUUID(), id, 4L,
                new PurchaseCaptured(accountId, authId, Money.of("200.00"),
                        Instant.parse("2025-01-01T00:00:03Z")));
        OutboxEvent payment = outbox(
                UUID.randomUUID(), id, 5L,
                new PaymentReceived(accountId, Money.of("200.00"),
                        Instant.parse("2025-01-01T00:00:04Z")));

        CreditAccountSummary s1 = projector.project(opened, Optional.empty()).summary();
        CreditAccountSummary s2 = projector.project(limit, Optional.of(s1)).summary();
        CreditAccountSummary s3 = projector.project(auth, Optional.of(s2)).summary();
        CreditAccountSummary s4 = projector.project(capture, Optional.of(s3)).summary();
        ProjectionTick tick = projector.project(payment, Optional.of(s4));

        assertThat(tick.summary().outstandingBalance()).isEqualTo("0.00");
        assertThat(tick.summary().availableLimit()).isEqualTo("1000.00");
    }

    @Test
    void idempotent_whenVersionAlreadyApplied() {
        UUID id = UUID.randomUUID();
        OutboxEvent opened = outbox(
                UUID.randomUUID(), id, 1L,
                new CreditAccountOpened(CreditAccountId.of(id), Instant.parse("2025-01-01T00:00:00Z")));

        CreditAccountSummary base = projector.project(opened, Optional.empty()).summary();
        ProjectionTick tick = projector.project(opened, Optional.of(base));

        assertThat(tick.applied()).isFalse();
        assertThat(tick.summary()).isEqualTo(base);
    }

    @Test
    void outOfOrder_event_isNotApplied() {
        UUID id = UUID.randomUUID();
        CreditAccountId accountId = CreditAccountId.of(id);
        OutboxEvent opened = outbox(
                UUID.randomUUID(), id, 1L,
                new CreditAccountOpened(accountId, Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(
                UUID.randomUUID(), id, 3L,
                new CreditLimitAssigned(accountId, Money.of("1000.00"), Instant.parse("2025-01-01T00:00:01Z")));

        CreditAccountSummary base = projector.project(opened, Optional.empty()).summary();
        ProjectionTick tick = projector.project(limit, Optional.of(base));

        assertThat(tick.applied()).isFalse();
        assertThat(tick.summary()).isEqualTo(base);
    }

    private OutboxEvent outbox(UUID eventId, UUID accountId, long version, CreditAccountEvent event) {
        return new OutboxEvent(
                eventId,
                "CreditAccount",
                accountId.toString(),
                version,
                event.getClass().getSimpleName(),
                event,
                java.util.Map.of(),
                event.occurredAt());
    }
}
