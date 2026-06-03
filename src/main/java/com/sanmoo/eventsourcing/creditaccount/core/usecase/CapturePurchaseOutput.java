package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record CapturePurchaseOutput(CreditAccountOutput account, boolean replayed) {}
