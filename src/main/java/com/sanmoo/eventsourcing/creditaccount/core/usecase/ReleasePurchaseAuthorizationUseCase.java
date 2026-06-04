package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReleasePurchaseAuthorizationUseCase {

    private final CreditAccountUseCaseSupport support;

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
