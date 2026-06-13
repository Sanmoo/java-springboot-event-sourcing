package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitChanged;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PaymentReceived;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorizationReleased;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorized;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCaptured;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreditAccountSummaryProjectorTest {

    private final CreditAccountSummaryProjector projector = new CreditAccountSummaryProjector();

    @Test
    void emptySummary_startsWithVersionZero() {
        var accountId = UUID.randomUUID();
        var event = openedEvent(accountId, 1L);
        var summary = projector.emptySummary(event);
        assertThat(summary.creditAccountId()).isEqualTo(accountId);
        assertThat(summary.projectedVersion()).isEqualTo(0L);
        assertThat(summary.opened()).isFalse();
    }

    @Test
    void apply_openedTransitionsToOpened() {
        var accountId = UUID.randomUUID();
        var event = openedEvent(accountId, 1L);
        var empty = projector.emptySummary(event);
        var after = projector.apply(event, empty);
        assertThat(after.opened()).isTrue();
        assertThat(after.projectedVersion()).isEqualTo(1L);
    }

    @Test
    void apply_assignedLimitComputesAvailable() {
        var accountId = UUID.randomUUID();
        var openedEvent = openedEvent(accountId, 1L);
        var opened = projector.apply(openedEvent, projector.emptySummary(openedEvent));

        var assigned = new CreditLimitAssigned(CreditAccountId.of(accountId), Money.of("500.00"), Instant.now());
        var assignedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2L,
                "CreditLimitAssigned", assigned, java.util.Map.of(), Instant.now());

        var after = projector.apply(assignedEvent, opened);
        assertThat(after.creditLimit()).isEqualTo("500.00");
        assertThat(after.availableLimit()).isEqualTo("500.00");
        assertThat(after.projectedVersion()).isEqualTo(2L);
    }

    @Test
    void apply_changedLimitRecomputesAvailable() {
        var accountId = UUID.randomUUID();
        var openedEvent = openedEvent(accountId, 1L);
        var opened = projector.apply(openedEvent, projector.emptySummary(openedEvent));

        var assigned = new CreditLimitAssigned(CreditAccountId.of(accountId), Money.of("500.00"), Instant.now());
        var assignedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2L,
                "CreditLimitAssigned", assigned, java.util.Map.of(), Instant.now());
        var afterAssign = projector.apply(assignedEvent, opened);

        var changed = new CreditLimitChanged(CreditAccountId.of(accountId), Money.of("800.00"), Instant.now());
        var changedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3L,
                "CreditLimitChanged", changed, java.util.Map.of(), Instant.now());
        var after = projector.apply(changedEvent, afterAssign);

        assertThat(after.creditLimit()).isEqualTo("800.00");
        assertThat(after.availableLimit()).isEqualTo("800.00");
        assertThat(after.projectedVersion()).isEqualTo(3L);
    }

    @Test
    void apply_paymentReceivedReducesOutstanding() {
        var accountId = UUID.randomUUID();
        var openedEvent = openedEvent(accountId, 1L);
        var opened = projector.apply(openedEvent, projector.emptySummary(openedEvent));

        var payment = new PaymentReceived(CreditAccountId.of(accountId), Money.of("100.00"), Instant.now());
        var paymentEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2L,
                "PaymentReceived", payment, java.util.Map.of(), Instant.now());
        var after = projector.apply(paymentEvent, opened);

        assertThat(after.outstandingBalance()).isEqualTo("-100.00");
        assertThat(after.projectedVersion()).isEqualTo(2L);
    }

    @Test
    void apply_purchaseAuthorizedCreatesAuth() {
        var accountId = UUID.randomUUID();
        var openedEvent = openedEvent(accountId, 1L);
        var opened = projector.apply(openedEvent, projector.emptySummary(openedEvent));

        var assigned = new CreditLimitAssigned(CreditAccountId.of(accountId), Money.of("500.00"), Instant.now());
        var assignedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2L,
                "CreditLimitAssigned", assigned, java.util.Map.of(), Instant.now());
        var afterAssign = projector.apply(assignedEvent, opened);

        var auth = new PurchaseAuthorized(CreditAccountId.of(accountId),
                new AuthorizationId(UUID.randomUUID()), Money.of("150.00"), "Store", Instant.now());
        var authEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3L,
                "PurchaseAuthorized", auth, java.util.Map.of(), Instant.now());
        var after = projector.apply(authEvent, afterAssign);

        assertThat(after.authorizedAmount()).isEqualTo("150.00");
        assertThat(after.availableLimit()).isEqualTo("350.00");
        assertThat(after.authorizations()).hasSize(1);
        assertThat(after.authorizations().get(0).status()).isEqualTo("OPEN");
        assertThat(after.projectedVersion()).isEqualTo(3L);
    }

    @Test
    void apply_purchaseCapturedMovesAuthorizedToOutstanding() {
        var accountId = UUID.randomUUID();
        var authorizationId = new AuthorizationId(UUID.randomUUID());
        var assigned = assignedSummary(accountId, "500.00");
        var auth = new PurchaseAuthorized(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), "Store", Instant.now());
        var authEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3L,
                "PurchaseAuthorized", auth, java.util.Map.of(), Instant.now());
        var authorized = projector.apply(authEvent, assigned);

        var captured = new PurchaseCaptured(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), Instant.now());
        var capturedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 4L,
                "PurchaseCaptured", captured, java.util.Map.of(), Instant.now());
        var after = projector.apply(capturedEvent, authorized);

        assertThat(after.outstandingBalance()).isEqualTo("150.00");
        assertThat(after.authorizedAmount()).isEqualTo("0.00");
        assertThat(after.availableLimit()).isEqualTo("350.00");
        assertThat(after.authorizations()).extracting(CreditAccountSummary.AuthorizationSummary::status)
                .containsExactly("CAPTURED");
        assertThat(after.projectedVersion()).isEqualTo(4L);
        assertThat(after.lastEventId()).isEqualTo(capturedEvent.eventId());
    }

    @Test
    void apply_purchaseAuthorizationReleasedRestoresAvailableLimit() {
        var accountId = UUID.randomUUID();
        var authorizationId = new AuthorizationId(UUID.randomUUID());
        var assigned = assignedSummary(accountId, "500.00");
        var auth = new PurchaseAuthorized(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), "Store", Instant.now());
        var authEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3L,
                "PurchaseAuthorized", auth, java.util.Map.of(), Instant.now());
        var authorized = projector.apply(authEvent, assigned);

        var released = new PurchaseAuthorizationReleased(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), Instant.now());
        var releasedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 4L,
                "PurchaseAuthorizationReleased", released, java.util.Map.of(), Instant.now());
        var after = projector.apply(releasedEvent, authorized);

        assertThat(after.authorizedAmount()).isEqualTo("0.00");
        assertThat(after.availableLimit()).isEqualTo("500.00");
        assertThat(after.authorizations()).extracting(CreditAccountSummary.AuthorizationSummary::status)
                .containsExactly("RELEASED");
        assertThat(after.projectedVersion()).isEqualTo(4L);
        assertThat(after.lastEventId()).isEqualTo(releasedEvent.eventId());
    }

    @Test
    void apply_purchaseCapturedLeavesNonMatchingAuthorizationOpen() {
        var accountId = UUID.randomUUID();
        var authorizationId = new AuthorizationId(UUID.randomUUID());
        var otherAuthorizationId = new AuthorizationId(UUID.randomUUID());
        var assigned = assignedSummary(accountId, "500.00");
        var auth = new PurchaseAuthorized(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), "Store", Instant.now());
        var authEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3L,
                "PurchaseAuthorized", auth, java.util.Map.of(), Instant.now());
        var authorized = projector.apply(authEvent, assigned);

        var captured = new PurchaseCaptured(CreditAccountId.of(accountId), otherAuthorizationId,
                Money.of("150.00"), Instant.now());
        var capturedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 4L,
                "PurchaseCaptured", captured, java.util.Map.of(), Instant.now());
        var after = projector.apply(capturedEvent, authorized);

        assertThat(after.authorizations()).extracting(CreditAccountSummary.AuthorizationSummary::status)
                .containsExactly("OPEN");
        assertThat(after.outstandingBalance()).isEqualTo("150.00");
        assertThat(after.authorizedAmount()).isEqualTo("0.00");
    }

    private OutboxEvent openedEvent(UUID accountId, long version) {
        var opened = new CreditAccountOpened(CreditAccountId.of(accountId), Instant.now());
        return new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), version,
                "CreditAccountOpened", opened, java.util.Map.of(), Instant.now());
    }

    private CreditAccountSummary assignedSummary(UUID accountId, String limit) {
        var openedEvent = openedEvent(accountId, 1L);
        var opened = projector.apply(openedEvent, projector.emptySummary(openedEvent));
        var assigned = new CreditLimitAssigned(CreditAccountId.of(accountId), Money.of(limit), Instant.now());
        var assignedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2L,
                "CreditLimitAssigned", assigned, java.util.Map.of(), Instant.now());
        return projector.apply(assignedEvent, opened);
    }
}
