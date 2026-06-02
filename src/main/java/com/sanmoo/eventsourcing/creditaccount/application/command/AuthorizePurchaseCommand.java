package com.sanmoo.eventsourcing.creditaccount.application.command;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;

public record AuthorizePurchaseCommand(String idempotencyKey, CreditAccountId creditAccountId, AuthorizationId authorizationId, Money amount, String merchantName) {}
