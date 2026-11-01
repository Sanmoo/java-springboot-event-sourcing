package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record AuthorizePurchaseResponse(
    @JsonUnwrapped CreditAccountResponse account, String authorizationId) {}
