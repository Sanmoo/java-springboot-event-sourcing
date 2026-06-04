package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthorizePurchaseUseCase {

    private final CreditAccountUseCaseSupport support;
    private final UniqueIdGenerator uniqueIdGenerator;

    public AuthorizePurchaseOutput execute(AuthorizePurchaseInput input) {
        var authorizationId = AuthorizationId.of(uniqueIdGenerator.generate());

        return support.executeIdempotent(
                input.idempotencyKey(),
                "AuthorizePurchase",
                input.creditAccountId(),
                input,
                account -> account.authorizePurchase(
                        authorizationId, input.amount(), input.merchantName(), now()),
                result -> new AuthorizePurchaseOutput(
                        result.output(),
                        authorizationId.value().toString(),
                        result.replayed()
                )
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
