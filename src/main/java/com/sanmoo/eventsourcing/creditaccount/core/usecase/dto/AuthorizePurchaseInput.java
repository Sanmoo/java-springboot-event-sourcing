package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;

public record AuthorizePurchaseInput(
        String idempotencyKey,
        CreditAccountId creditAccountId,
        AuthorizationId authorizationId,
        Money amount,
        String merchantName
) {}
