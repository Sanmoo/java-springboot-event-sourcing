package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthorizePurchaseUseCase {

    private final CreditAccountUseCaseSupport support;

    public AuthorizePurchaseOutput execute(AuthorizePurchaseInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "AuthorizePurchase",
                input.creditAccountId(),
                input,
                account -> account.authorizePurchase(input.authorizationId(), input.amount(), input.merchantName(), now()),
                result -> new AuthorizePurchaseOutput(
                        result.output(),
                        input.authorizationId().value().toString(),
                        result.replayed()
                )
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
