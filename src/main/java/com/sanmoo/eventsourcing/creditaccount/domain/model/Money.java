package com.sanmoo.eventsourcing.creditaccount.domain.model;

import com.sanmoo.eventsourcing.creditaccount.domain.error.InvalidMoneyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(String amount) {
        return positive(new BigDecimal(amount));
    }

    public static Money positive(BigDecimal amount) {
        Money money = new Money(amount);
        if (money.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidMoneyException("amount must be positive");
        }
        return money;
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(Money other) {
        return this.amount.compareTo(other.amount);
    }
}
