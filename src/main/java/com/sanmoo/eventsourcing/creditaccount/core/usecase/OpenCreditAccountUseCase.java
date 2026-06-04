package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OpenCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;

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
