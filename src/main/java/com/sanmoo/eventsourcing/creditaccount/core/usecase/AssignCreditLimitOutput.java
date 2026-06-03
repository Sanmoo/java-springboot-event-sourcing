package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record AssignCreditLimitOutput(CreditAccountOutput account, boolean replayed) {}
