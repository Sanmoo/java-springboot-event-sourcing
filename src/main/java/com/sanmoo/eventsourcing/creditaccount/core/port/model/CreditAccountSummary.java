package com.sanmoo.eventsourcing.creditaccount.core.port.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreditAccountSummary(
        UUID creditAccountId,
        boolean opened,
        String creditLimit,
        String outstandingBalance,
        String authorizedAmount,
        String availableLimit,
        List<AuthorizationSummary> authorizations,
        long projectedVersion,
        UUID lastEventId,
        Instant updatedAt
) {
    public record AuthorizationSummary(
            UUID authorizationId,
            String amount,
            String status,
            String merchantName
    ) {}
}
