package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.PurchaseAuthorizationOutput;

public final class CreditAccountDtoMapper {

  private CreditAccountDtoMapper() {}

  public static CreditAccountResponse toResponse(CreditAccountOutput output) {
    return new CreditAccountResponse(
        output.creditAccountId(),
        output.opened(),
        output.creditLimit(),
        output.outstandingBalance(),
        output.authorizedAmount(),
        output.availableLimit(),
        output.projectedVersion(),
        output.authorizations().stream().map(CreditAccountDtoMapper::toResponse).toList());
  }

  public static AuthorizationResponse toResponse(PurchaseAuthorizationOutput output) {
    return new AuthorizationResponse(
        output.authorizationId(), output.amount(), output.status(), output.merchantName());
  }
}
