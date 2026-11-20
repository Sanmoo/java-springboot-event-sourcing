package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record BlockCreditAccountOutput(CreditAccountOutput account, boolean replayed) {}
