package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

public record GetCreditAccountInput(CreditAccountId creditAccountId) {}
