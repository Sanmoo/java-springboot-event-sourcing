package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

public record CapturePurchaseInput(String idempotencyKey, CreditAccountId creditAccountId, AuthorizationId authorizationId) {}
