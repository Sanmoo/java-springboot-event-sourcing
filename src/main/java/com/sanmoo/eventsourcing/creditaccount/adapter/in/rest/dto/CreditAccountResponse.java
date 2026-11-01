package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.util.List;

public record CreditAccountResponse(
    String creditAccountId,
    boolean opened,
    String creditLimit,
    String outstandingBalance,
    String authorizedAmount,
    String availableLimit,
    Long projectedVersion,
    List<AuthorizationResponse> authorizations) {
  public CreditAccountResponse {
    if (authorizations != null) {
      authorizations = List.copyOf(authorizations);
    }
  }
}
