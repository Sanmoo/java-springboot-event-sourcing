package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

public record AuthorizationResponse(
    String authorizationId, String amount, String status, String merchantName) {}
