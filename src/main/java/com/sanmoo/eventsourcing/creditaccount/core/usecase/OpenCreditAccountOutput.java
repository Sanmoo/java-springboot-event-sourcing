package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record OpenCreditAccountOutput(CreditAccountOutput account, boolean replayed) {}
