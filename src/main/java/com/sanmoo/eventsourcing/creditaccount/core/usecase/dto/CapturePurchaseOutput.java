package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record CapturePurchaseOutput(CreditAccountOutput account, boolean replayed) {}
