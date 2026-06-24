package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

public record AuthorizePurchaseResponse(
        CreditAccountResponse account,
        String authorizationId
) {
}
