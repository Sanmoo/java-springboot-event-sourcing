package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record OpenCreditAccountOutput(CreditAccountOutput account, boolean replayed) {}
