package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.util.List;

public record CreditAccountOutput(
        String creditAccountId,
        boolean opened,
        String creditLimit,
        String outstandingBalance,
        String authorizedAmount,
        String availableLimit,
        List<PurchaseAuthorizationOutput> authorizations
) {}
