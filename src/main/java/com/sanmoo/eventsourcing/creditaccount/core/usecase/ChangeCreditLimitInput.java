package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;

public record ChangeCreditLimitInput(String idempotencyKey, CreditAccountId creditAccountId, Money newCreditLimit) {}
