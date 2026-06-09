package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record AssignCreditLimitOutput(CreditAccountOutput account, boolean replayed) {}
