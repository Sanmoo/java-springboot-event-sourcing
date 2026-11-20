package com.sanmoo.eventsourcing.creditaccount.domain.model;

import java.util.Map;

public record CreditAccountSnapshot(
        CreditAccountId id,
        boolean opened,
        boolean blocked,
        Money creditLimit,
        Money outstandingBalance,
        Money authorizedAmount,
        Money availableLimit,
        Map<AuthorizationId, PurchaseAuthorization> authorizations
) {}
