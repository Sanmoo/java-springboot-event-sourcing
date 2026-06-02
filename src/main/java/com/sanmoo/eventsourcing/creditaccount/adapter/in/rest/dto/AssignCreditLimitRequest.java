package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssignCreditLimitRequest(@NotNull @JsonProperty("limit") BigDecimal limit) {
}
