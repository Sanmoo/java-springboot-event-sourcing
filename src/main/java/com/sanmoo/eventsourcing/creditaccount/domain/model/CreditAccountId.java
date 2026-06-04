package com.sanmoo.eventsourcing.creditaccount.domain.model;

import java.util.UUID;

public record CreditAccountId(UUID value) {
    public CreditAccountId {
        java.util.Objects.requireNonNull(value, "value must not be null");
    }

    public static CreditAccountId of(UUID value) { return new CreditAccountId(value); }
}
