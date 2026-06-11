package com.sanmoo.eventsourcing.creditaccount.domain;

import com.sanmoo.eventsourcing.creditaccount.domain.error.*;
import com.sanmoo.eventsourcing.creditaccount.domain.event.*;
import com.sanmoo.eventsourcing.creditaccount.domain.model.*;

import java.time.Instant;
import java.util.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CreditAccount {
    private final CreditAccountId id;
    private boolean opened;
    private Money creditLimit;
    private Money outstandingBalance = Money.zero();
    private Money authorizedAmount = Money.zero();
    private final Map<AuthorizationId, PurchaseAuthorization> authorizations = new LinkedHashMap<>();
    @Getter
    @Accessors(fluent = true)
    private long version;

    public static CreditAccount rehydrate(CreditAccountId id, List<CreditAccountEvent> history) {
        CreditAccount account = new CreditAccount(id);
        history.forEach(event -> { account.apply(event); account.version++; });
        return account;
    }

    public List<CreditAccountEvent> open(Instant occurredAt) {
        if (opened) { throw new AccountAlreadyExistsException("credit account already exists"); }
        return recordThat(new CreditAccountOpened(id, occurredAt));
    }

    public List<CreditAccountEvent> assignCreditLimit(Money limit, Instant occurredAt) {
        ensureOpened();
        if (creditLimit != null) { throw new CreditLimitAlreadyAssignedException("credit limit already assigned"); }
        if (!limit.isGreaterThan(Money.zero())) { throw new InvalidCreditLimitException("credit limit must be positive"); }
        return recordThat(new CreditLimitAssigned(id, limit, occurredAt));
    }

    public List<CreditAccountEvent> changeCreditLimit(Money newLimit, Instant occurredAt) {
        ensureOpened(); ensureLimitAssigned();
        if (!newLimit.isGreaterThan(Money.zero())) { throw new InvalidCreditLimitException("credit limit must be positive"); }
        Money committed = outstandingBalance.plus(authorizedAmount);
        if (newLimit.isLessThan(committed)) { throw new InvalidCreditLimitException("new limit is lower than committed balance"); }
        return recordThat(new CreditLimitChanged(id, newLimit, occurredAt));
    }

    public List<CreditAccountEvent> authorizePurchase(AuthorizationId authorizationId, Money amount, String merchantName, Instant occurredAt) {
        ensureOpened(); ensureLimitAssigned();
        if (authorizations.containsKey(authorizationId)) { throw new AuthorizationAlreadyExistsException("authorization already exists"); }
        if (!amount.isGreaterThan(Money.zero())) { throw new InvalidMoneyException("purchase amount must be positive"); }
        if (amount.isGreaterThan(availableLimit())) { throw new InsufficientAvailableLimitException("insufficient available limit"); }
        return recordThat(new PurchaseAuthorized(id, authorizationId, amount, merchantName, occurredAt));
    }

    public List<CreditAccountEvent> capturePurchase(AuthorizationId authorizationId, Instant occurredAt) {
        ensureOpened();
        PurchaseAuthorization authorization = openAuthorization(authorizationId);
        return List.of(new PurchaseCaptured(id, authorizationId, authorization.amount(), occurredAt));
    }

    public List<CreditAccountEvent> releasePurchaseAuthorization(AuthorizationId authorizationId, Instant occurredAt) {
        ensureOpened();
        PurchaseAuthorization authorization = openAuthorization(authorizationId);
        return List.of(new PurchaseAuthorizationReleased(id, authorizationId, authorization.amount(), occurredAt));
    }

    public List<CreditAccountEvent> receivePayment(Money amount, Instant occurredAt) {
        ensureOpened();
        if (!amount.isGreaterThan(Money.zero())) { throw new InvalidMoneyException("payment amount must be positive"); }
        if (amount.isGreaterThan(outstandingBalance)) { throw new PaymentExceedsOutstandingBalanceException("payment exceeds outstanding balance"); }
        return List.of(new PaymentReceived(id, amount, occurredAt));
    }

    public void applyAll(List<CreditAccountEvent> events) {
        events.forEach(event -> { apply(event); version++; });
    }

    public CreditAccountSnapshot snapshot() {
        return new CreditAccountSnapshot(id, opened, creditLimit, outstandingBalance, authorizedAmount, availableLimit(), Map.copyOf(authorizations));
    }

    private Money availableLimit() {
        if (creditLimit == null) {
            return Money.zero();
        }
        return creditLimit.minus(outstandingBalance).minus(authorizedAmount);
    }

    private PurchaseAuthorization openAuthorization(AuthorizationId authorizationId) {
        PurchaseAuthorization authorization = authorizations.get(authorizationId);
        if (authorization == null) { throw new AuthorizationNotFoundException("authorization not found"); }
        if (authorization.status() != PurchaseAuthorizationStatus.OPEN) { throw new AuthorizationNotOpenException("authorization is not open"); }
        return authorization;
    }

    private List<CreditAccountEvent> recordThat(CreditAccountEvent event) {
        apply(event);
        version++;
        return List.of(event);
    }

    private void ensureOpened() { if (!opened) { throw new AccountNotFoundException("credit account not found"); } }
    private void ensureLimitAssigned() { if (creditLimit == null) { throw new CreditLimitNotAssignedException("credit limit not assigned"); } }

    private void apply(CreditAccountEvent event) {
        switch (event) {
            case CreditAccountOpened _ -> opened = true;
            case CreditLimitAssigned e -> creditLimit = e.limit();
            case CreditLimitChanged e -> creditLimit = e.newLimit();
            case PurchaseAuthorized e -> {
                authorizations.put(e.authorizationId(), new PurchaseAuthorization(e.authorizationId(), e.amount(), PurchaseAuthorizationStatus.OPEN, e.merchantName()));
                authorizedAmount = authorizedAmount.plus(e.amount());
            }
            case PurchaseCaptured e -> {
                PurchaseAuthorization authorization = authorizations.get(e.authorizationId());
                authorizations.put(e.authorizationId(), authorization.capture());
                authorizedAmount = authorizedAmount.minus(e.amount());
                outstandingBalance = outstandingBalance.plus(e.amount());
            }
            case PurchaseAuthorizationReleased e -> {
                PurchaseAuthorization authorization = authorizations.get(e.authorizationId());
                authorizations.put(e.authorizationId(), authorization.release());
                authorizedAmount = authorizedAmount.minus(e.amount());
            }
            case PaymentReceived e -> outstandingBalance = outstandingBalance.minus(e.amount());
        }
    }
}
