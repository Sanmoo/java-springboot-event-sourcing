package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record UnblockCreditAccountOutput(CreditAccountOutput account, boolean replayed) {}
