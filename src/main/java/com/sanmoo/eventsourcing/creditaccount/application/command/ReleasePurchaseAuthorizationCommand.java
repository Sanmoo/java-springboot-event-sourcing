package com.sanmoo.eventsourcing.creditaccount.application.command;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;

public record ReleasePurchaseAuthorizationCommand(String idempotencyKey, CreditAccountId creditAccountId, AuthorizationId authorizationId) {}
