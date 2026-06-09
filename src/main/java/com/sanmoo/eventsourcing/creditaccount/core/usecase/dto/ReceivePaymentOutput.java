package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record ReceivePaymentOutput(CreditAccountOutput account, boolean replayed) {}
