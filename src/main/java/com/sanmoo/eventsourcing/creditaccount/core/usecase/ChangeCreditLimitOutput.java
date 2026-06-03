package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record ChangeCreditLimitOutput(CreditAccountOutput account, boolean replayed) {}
