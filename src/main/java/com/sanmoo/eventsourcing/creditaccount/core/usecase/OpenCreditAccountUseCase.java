package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.CreditAccount;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

import java.time.Instant;

public class OpenCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;

    public OpenCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public OpenCreditAccountOutput execute(OpenCreditAccountInput input) {
        CreditAccountId creditAccountId = CreditAccountId.newId();
        return support.executeIdempotent(
                input.idempotencyKey(),
                "OpenCreditAccount",
                creditAccountId,
                input,
                account -> account.open(now()),
                result -> new OpenCreditAccountOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
