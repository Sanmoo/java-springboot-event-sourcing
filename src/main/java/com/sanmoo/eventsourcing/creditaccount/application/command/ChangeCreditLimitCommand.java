package com.sanmoo.eventsourcing.creditaccount.application.command;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;

public record ChangeCreditLimitCommand(String idempotencyKey, CreditAccountId creditAccountId, Money newCreditLimit) {}
