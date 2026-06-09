package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record ChangeCreditLimitOutput(CreditAccountOutput account, boolean replayed) {}
