package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AssignCreditLimitUseCase {

    private final CreditAccountUseCaseSupport support;

    public AssignCreditLimitOutput execute(AssignCreditLimitInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "AssignCreditLimit",
                input.creditAccountId(),
                input,
                account -> account.assignCreditLimit(input.creditLimit(), now()),
                result -> new AssignCreditLimitOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
