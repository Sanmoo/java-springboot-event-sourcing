package com.sanmoo.eventsourcing.creditaccount.domain;

import com.sanmoo.eventsourcing.creditaccount.domain.error.InvalidMoneyException;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
}
