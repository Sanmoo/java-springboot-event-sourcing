package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record AuthorizePurchaseOutput(
        CreditAccountOutput account,
        String authorizationId,
        boolean replayed
) {}
