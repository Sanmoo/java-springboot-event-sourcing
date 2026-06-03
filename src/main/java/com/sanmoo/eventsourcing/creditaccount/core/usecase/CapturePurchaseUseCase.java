package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;

public class CapturePurchaseUseCase {

    private final CreditAccountUseCaseSupport support;

    public CapturePurchaseUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public CapturePurchaseOutput execute(CapturePurchaseInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "CapturePurchase",
                input.creditAccountId(),
                input,
                account -> account.capturePurchase(input.authorizationId(), now()),
                result -> new CapturePurchaseOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
