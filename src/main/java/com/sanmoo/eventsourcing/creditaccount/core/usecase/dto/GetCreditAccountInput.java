package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

public record GetCreditAccountInput(CreditAccountId creditAccountId, Long minVersion) {
    public static GetCreditAccountInput of(CreditAccountId id) {
        return new GetCreditAccountInput(id, null);
    }
    public static GetCreditAccountInput of(CreditAccountId id, Long minVersion) {
        return new GetCreditAccountInput(id, minVersion);
    }
}
