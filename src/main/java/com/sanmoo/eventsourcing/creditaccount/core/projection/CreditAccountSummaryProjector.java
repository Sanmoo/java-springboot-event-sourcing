package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CreditAccountSummaryProjector {

    public CreditAccountSummary apply(OutboxEvent event, CreditAccountSummary base) {
        return apply(base, event.event(), event.eventId());
    }

    public CreditAccountSummary emptySummary(OutboxEvent event) {
        UUID id = UUID.fromString(event.aggregateId());
        return new CreditAccountSummary(
                id, false, null, "0.00", "0.00", "0.00",
                List.of(), 0L, null, event.occurredAt());
    }

    private CreditAccountSummary apply(CreditAccountSummary s, CreditAccountEvent event, UUID lastEventId) {
        if (event instanceof CreditAccountOpened opened) {
            return new CreditAccountSummary(
                    s.creditAccountId(), true, null,
                    "0.00", "0.00", "0.00",
                    List.of(), s.projectedVersion() + 1, lastEventId, opened.occurredAt());
        }
        if (event instanceof CreditLimitAssigned assigned) {
            BigDecimal limit = assigned.limit().amount();
            BigDecimal outstanding = new BigDecimal(s.outstandingBalance());
            BigDecimal authorized = new BigDecimal(s.authorizedAmount());
            return new CreditAccountSummary(
                    s.creditAccountId(), s.opened(), assigned.limit().amount().toPlainString(),
                    s.outstandingBalance(), s.authorizedAmount(),
                    limit.subtract(outstanding).subtract(authorized).toPlainString(),
                    s.authorizations(), s.projectedVersion() + 1, lastEventId, assigned.occurredAt());
        }
        if (event instanceof CreditLimitChanged changed) {
            BigDecimal newLimit = changed.newLimit().amount();
            BigDecimal outstanding = new BigDecimal(s.outstandingBalance());
            BigDecimal authorized = new BigDecimal(s.authorizedAmount());
            String newAvailable = newLimit.subtract(outstanding).subtract(authorized).toPlainString();
            return new CreditAccountSummary(
                    s.creditAccountId(), s.opened(), changed.newLimit().amount().toPlainString(),
                    s.outstandingBalance(), s.authorizedAmount(), newAvailable,
                    s.authorizations(), s.projectedVersion() + 1, lastEventId, changed.occurredAt());
        }
        if (event instanceof PurchaseAuthorized auth) {
            BigDecimal amount = auth.amount().amount();
            BigDecimal newAuthorized = new BigDecimal(s.authorizedAmount()).add(amount);
            BigDecimal newAvailable = new BigDecimal(s.availableLimit()).subtract(amount);
            List<CreditAccountSummary.AuthorizationSummary> auths = new ArrayList<>(s.authorizations());
            auths.add(new CreditAccountSummary.AuthorizationSummary(
                    auth.authorizationId().value(), auth.amount().amount().toPlainString(), "OPEN", auth.merchantName()));
            return new CreditAccountSummary(
                    s.creditAccountId(), s.opened(), s.creditLimit(),
                    s.outstandingBalance(), newAuthorized.toPlainString(), newAvailable.toPlainString(),
                    auths, s.projectedVersion() + 1, lastEventId, auth.occurredAt());
        }
        if (event instanceof PurchaseCaptured captured) {
            List<CreditAccountSummary.AuthorizationSummary> updated = s.authorizations().stream()
                    .map(a -> a.authorizationId().equals(captured.authorizationId().value())
                            ? new CreditAccountSummary.AuthorizationSummary(
                                    a.authorizationId(), a.amount(), "CAPTURED", a.merchantName())
                            : a)
                    .toList();
            BigDecimal capturedAmount = captured.amount().amount();
            BigDecimal newOutstanding = new BigDecimal(s.outstandingBalance()).add(capturedAmount);
            BigDecimal newAuthorized = new BigDecimal(s.authorizedAmount()).subtract(capturedAmount);
            return new CreditAccountSummary(
                    s.creditAccountId(), s.opened(), s.creditLimit(),
                    newOutstanding.toPlainString(), newAuthorized.toPlainString(), s.availableLimit(),
                    updated, s.projectedVersion() + 1, lastEventId, captured.occurredAt());
        }
        if (event instanceof PurchaseAuthorizationReleased released) {
            List<CreditAccountSummary.AuthorizationSummary> updated = s.authorizations().stream()
                    .map(a -> a.authorizationId().equals(released.authorizationId().value())
                            ? new CreditAccountSummary.AuthorizationSummary(
                                    a.authorizationId(), a.amount(), "RELEASED", a.merchantName())
                            : a)
                    .toList();
            BigDecimal releasedAmount = released.amount().amount();
            BigDecimal newAuthorized = new BigDecimal(s.authorizedAmount()).subtract(releasedAmount);
            BigDecimal newAvailable = new BigDecimal(s.availableLimit()).add(releasedAmount);
            return new CreditAccountSummary(
                    s.creditAccountId(), s.opened(), s.creditLimit(),
                    s.outstandingBalance(), newAuthorized.toPlainString(), newAvailable.toPlainString(),
                    updated, s.projectedVersion() + 1, lastEventId, released.occurredAt());
        }
        if (event instanceof PaymentReceived payment) {
            BigDecimal newOutstanding = new BigDecimal(s.outstandingBalance())
                    .subtract(payment.amount().amount());
            BigDecimal limit = s.creditLimit() != null ? new BigDecimal(s.creditLimit()) : BigDecimal.ZERO;
            BigDecimal newAvailable = limit.subtract(newOutstanding).subtract(new BigDecimal(s.authorizedAmount()));
            return new CreditAccountSummary(
                    s.creditAccountId(), s.opened(), s.creditLimit(),
                    newOutstanding.toPlainString(), s.authorizedAmount(), newAvailable.toPlainString(),
                    s.authorizations(), s.projectedVersion() + 1, lastEventId, payment.occurredAt());
        }
        return s;
    }
}
