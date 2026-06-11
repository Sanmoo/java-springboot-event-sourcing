package com.sanmoo.eventsourcing.creditaccount.domain;

import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountAlreadyExistsException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AuthorizationAlreadyExistsException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AuthorizationNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AuthorizationNotOpenException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.CreditLimitAlreadyAssignedException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.CreditLimitNotAssignedException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.InsufficientAvailableLimitException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.InvalidCreditLimitException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.InvalidMoneyException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.PaymentExceedsOutstandingBalanceException;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitChanged;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PaymentReceived;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorized;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCaptured;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorizationReleased;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import com.sanmoo.eventsourcing.creditaccount.domain.model.PurchaseAuthorizationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditAccountTest {

    @Test
    void moneyRejectsZeroAndNegativeAmounts() {
        assertThatThrownBy(() -> Money.of("0.00"))
                .isInstanceOf(InvalidMoneyException.class);

        assertThatThrownBy(() -> Money.of("-1.00"))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void moneySupportsArithmetic() {
        assertThat(Money.of("10.00").plus(Money.of("2.50"))).isEqualTo(Money.of("12.50"));
        assertThat(Money.of("10.00").minus(Money.of("2.50"))).isEqualTo(Money.of("7.50"));
        assertThat(Money.of("10.00").isGreaterThan(Money.of("9.99"))).isTrue();
        assertThat(Money.zero().amount()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void opensCreditAccount() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());

        CreditAccount account = CreditAccount.rehydrate(accountId, List.of());
        var events = account.open(Instant.parse("2026-06-01T10:00:00Z"));

        assertThat(events).containsExactly(new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")));
        assertThat(account.snapshot().opened()).isTrue();
        assertThat(account.version()).isEqualTo(1L);
    }

    @Test
    void authorizesPurchaseWhenAvailableLimitIsEnough() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
        ));

        var events = account.authorizePurchase(authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z"));

        assertThat(events).containsExactly(new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z")));
        assertThat(account.snapshot().authorizedAmount()).isEqualTo(Money.of("25.00"));
        assertThat(account.snapshot().authorizations()).containsKey(authorizationId);
        assertThat(account.version()).isEqualTo(3L);
    }

    @Test
    void rejectsPurchaseWhenAvailableLimitIsInsufficient() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
        ));

        assertThatThrownBy(() -> account.authorizePurchase(AuthorizationId.of(UUID.randomUUID()), Money.of("101.00"), "Book Store", Instant.now()))
                .isInstanceOf(InsufficientAvailableLimitException.class);
    }

    @Test
    void captureMovesReservedAmountToOutstandingBalance() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
                new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z"))
        ));

        var events = account.capturePurchase(authorizationId, Instant.parse("2026-06-01T10:03:00Z"));

        assertThat(events).containsExactly(new PurchaseCaptured(accountId, authorizationId, Money.of("25.00"), Instant.parse("2026-06-01T10:03:00Z")));
        assertThat(account.snapshot().outstandingBalance()).isEqualTo(Money.of("25.00"));
        assertThat(account.snapshot().authorizedAmount()).isEqualTo(Money.zero());
        assertThat(account.version()).isEqualTo(4L);
    }

    @Test
    void cannotAssignLimitBeforeOpen() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of());

        assertThatThrownBy(() -> account.assignCreditLimit(Money.of("100.00"), Instant.now()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void cannotAssignLimitTwice() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
        ));

        assertThatThrownBy(() -> account.assignCreditLimit(Money.of("200.00"), Instant.now()))
                .isInstanceOf(CreditLimitAlreadyAssignedException.class);
    }

    @Test
    void cannotReduceLimitBelowOutstandingPlusAuthorized() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
                new PurchaseAuthorized(accountId, authorizationId, Money.of("80.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z"))
        ));

        assertThatThrownBy(() -> account.changeCreditLimit(Money.of("70.00"), Instant.now()))
                .isInstanceOf(InvalidCreditLimitException.class);
    }

    @Test
    void cannotCaptureMissingAuthorization() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
        ));

        assertThatThrownBy(() -> account.capturePurchase(AuthorizationId.of(UUID.randomUUID()), Instant.now()))
                .isInstanceOf(AuthorizationNotFoundException.class);
    }

    @Test
    void cannotReleaseCapturedAuthorization() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
                new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z")),
                new PurchaseCaptured(accountId, authorizationId, Money.of("25.00"), Instant.parse("2026-06-01T10:03:00Z"))
        ));

        assertThatThrownBy(() -> account.releasePurchaseAuthorization(authorizationId, Instant.now()))
                .isInstanceOf(AuthorizationNotOpenException.class);
    }

    @Test
    void cannotPayMoreThanOutstanding() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
        ));

        assertThatThrownBy(() -> account.receivePayment(Money.of("10.00"), Instant.now()))
                .isInstanceOf(PaymentExceedsOutstandingBalanceException.class);
    }

    @Test
    void cannotOpenAccountTwice() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z"))
        ));

        assertThatThrownBy(() -> account.open(Instant.now()))
                .isInstanceOf(AccountAlreadyExistsException.class);
    }

    @Test
    void assignCreditLimitOnOpenedAccount() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z"))
        ));

        var events = account.assignCreditLimit(Money.of("500.00"), Instant.parse("2026-06-01T10:01:00Z"));

        assertThat(events).containsExactly(new CreditLimitAssigned(accountId, Money.of("500.00"), Instant.parse("2026-06-01T10:01:00Z")));
        assertThat(account.snapshot().creditLimit()).isEqualTo(Money.of("500.00"));
        assertThat(account.version()).isEqualTo(2L);
    }

    @Test
    void cannotAssignZeroOrNegativeLimit() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z"))
        ));

        assertThatThrownBy(() -> account.assignCreditLimit(Money.zero(), Instant.now()))
                .isInstanceOf(InvalidCreditLimitException.class);
    }

    @Test
    void changeCreditLimitHappyPath() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
        ));

        var events = account.changeCreditLimit(Money.of("150.00"), Instant.parse("2026-06-01T10:02:00Z"));

        assertThat(events).containsExactly(new CreditLimitChanged(accountId, Money.of("150.00"), Instant.parse("2026-06-01T10:02:00Z")));
        assertThat(account.snapshot().creditLimit()).isEqualTo(Money.of("150.00"));
        assertThat(account.version()).isEqualTo(3L);
    }

    @Test
    void cannotChangeLimitToZeroOrNegative() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
        ));

        assertThatThrownBy(() -> account.changeCreditLimit(Money.zero(), Instant.now()))
                .isInstanceOf(InvalidCreditLimitException.class);
    }

    @Test
    void cannotAuthorizePurchaseWithoutLimit() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z"))
        ));

        assertThatThrownBy(() -> account.authorizePurchase(AuthorizationId.of(UUID.randomUUID()), Money.of("10.00"), "Store", Instant.now()))
                .isInstanceOf(CreditLimitNotAssignedException.class);
    }

    @Test
    void cannotAuthorizePurchaseWithDuplicateAuthorizationId() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
                new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Store", Instant.parse("2026-06-01T10:02:00Z"))
        ));

        assertThatThrownBy(() -> account.authorizePurchase(authorizationId, Money.of("10.00"), "Store", Instant.now()))
                .isInstanceOf(AuthorizationAlreadyExistsException.class);
    }

    @Test
    void cannotAuthorizePurchaseWithZeroAmount() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
        ));

        assertThatThrownBy(() -> account.authorizePurchase(AuthorizationId.of(UUID.randomUUID()), Money.zero(), "Store", Instant.now()))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void releaseAuthorizationHappyPath() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
                new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z"))
        ));

        var events = account.releasePurchaseAuthorization(authorizationId, Instant.parse("2026-06-01T10:03:00Z"));

        assertThat(events).containsExactly(new PurchaseAuthorizationReleased(accountId, authorizationId, Money.of("25.00"), Instant.parse("2026-06-01T10:03:00Z")));
        assertThat(account.snapshot().authorizedAmount()).isEqualTo(Money.zero());
        assertThat(account.snapshot().authorizations().get(authorizationId).status()).isEqualTo(PurchaseAuthorizationStatus.RELEASED);
        assertThat(account.version()).isEqualTo(4L);
    }

    @Test
    void cannotReleaseAlreadyReleasedAuthorization() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
                new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z")),
                new PurchaseAuthorizationReleased(accountId, authorizationId, Money.of("25.00"), Instant.parse("2026-06-01T10:03:00Z"))
        ));

        assertThatThrownBy(() -> account.releasePurchaseAuthorization(authorizationId, Instant.now()))
                .isInstanceOf(AuthorizationNotOpenException.class);
    }

    @Test
    void receivePaymentHappyPath() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
                new PurchaseAuthorized(accountId, authorizationId, Money.of("50.00"), "Store", Instant.parse("2026-06-01T10:02:00Z")),
                new PurchaseCaptured(accountId, authorizationId, Money.of("50.00"), Instant.parse("2026-06-01T10:03:00Z"))
        ));

        var events = account.receivePayment(Money.of("25.00"), Instant.parse("2026-06-01T10:04:00Z"));

        assertThat(events).containsExactly(new PaymentReceived(accountId, Money.of("25.00"), Instant.parse("2026-06-01T10:04:00Z")));
        assertThat(account.snapshot().outstandingBalance()).isEqualTo(Money.of("25.00"));
        assertThat(account.version()).isEqualTo(5L);
    }

    @Test
    void cannotReceiveZeroPayment() {
        CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
        CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
                new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
                new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
        ));

        assertThatThrownBy(() -> account.receivePayment(Money.zero(), Instant.now()))
                .isInstanceOf(InvalidMoneyException.class);
    }
}
