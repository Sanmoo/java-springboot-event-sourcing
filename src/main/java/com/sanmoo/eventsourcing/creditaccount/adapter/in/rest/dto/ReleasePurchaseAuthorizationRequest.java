package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleasePurchaseAuthorizationRequest() {
}
