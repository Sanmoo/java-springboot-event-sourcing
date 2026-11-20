package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountBlockRequest(@Valid @Size(max = 200) String reason) {}
