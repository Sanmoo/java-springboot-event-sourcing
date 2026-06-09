package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record ReleasePurchaseAuthorizationOutput(CreditAccountOutput account, boolean replayed) {}
