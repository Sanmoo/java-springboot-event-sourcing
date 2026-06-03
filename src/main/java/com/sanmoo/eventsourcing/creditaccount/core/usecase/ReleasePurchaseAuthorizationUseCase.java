package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;

public class ReleasePurchaseAuthorizationUseCase {

    private final CreditAccountUseCaseSupport support;

    public ReleasePurchaseAuthorizationUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public ReleasePurchaseAuthorizationOutput execute(ReleasePurchaseAuthorizationInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "ReleasePurchaseAuthorization",
                input.creditAccountId(),
                input,
                account -> account.releasePurchaseAuthorization(input.authorizationId(), now()),
                result -> new ReleasePurchaseAuthorizationOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
