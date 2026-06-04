package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;

public record AuthorizePurchaseInput(
        String idempotencyKey,
        CreditAccountId creditAccountId,
        Money amount,
        String merchantName
) {}
