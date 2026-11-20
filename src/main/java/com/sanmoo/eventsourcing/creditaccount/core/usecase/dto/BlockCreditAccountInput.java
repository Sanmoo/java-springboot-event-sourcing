package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

public record BlockCreditAccountInput(String idempotencyKey, CreditAccountId creditAccountId, String reason) {}
