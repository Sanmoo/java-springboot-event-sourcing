package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ChangeCreditLimitUseCase {

    private final CreditAccountUseCaseSupport support;

    public ChangeCreditLimitUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public ChangeCreditLimitOutput execute(ChangeCreditLimitInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "ChangeCreditLimit",
                input.creditAccountId(),
                input,
                account -> account.changeCreditLimit(input.newCreditLimit(), now()),
                result -> new ChangeCreditLimitOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
