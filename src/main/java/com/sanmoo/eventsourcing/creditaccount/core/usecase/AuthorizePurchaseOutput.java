package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record AuthorizePurchaseOutput(
        CreditAccountOutput account,
        String authorizationId,
        boolean replayed
) {}
