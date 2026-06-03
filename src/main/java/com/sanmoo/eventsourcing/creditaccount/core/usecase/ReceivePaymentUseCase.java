package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;

public class ReceivePaymentUseCase {

    private final CreditAccountUseCaseSupport support;

    public ReceivePaymentUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public ReceivePaymentOutput execute(ReceivePaymentInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "ReceivePayment",
                input.creditAccountId(),
                input,
                account -> account.receivePayment(input.amount(), now()),
                result -> new ReceivePaymentOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
