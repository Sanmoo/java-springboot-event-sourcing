package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record ReceivePaymentOutput(CreditAccountOutput account, boolean replayed) {}
