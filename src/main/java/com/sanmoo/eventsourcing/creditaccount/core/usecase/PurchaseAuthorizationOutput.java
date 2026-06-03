package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record PurchaseAuthorizationOutput(
        String authorizationId,
        String amount,
        String status,
        String merchantName
) {}
