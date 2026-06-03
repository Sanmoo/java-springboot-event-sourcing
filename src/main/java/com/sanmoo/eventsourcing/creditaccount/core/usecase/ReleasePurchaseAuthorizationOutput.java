package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record ReleasePurchaseAuthorizationOutput(CreditAccountOutput account, boolean replayed) {}
