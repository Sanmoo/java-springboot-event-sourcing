package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class AuthorizePurchaseUseCase {

    private final CreditAccountUseCaseSupport support;
    private final UniqueIdGenerator uniqueIdGenerator;

    public AuthorizePurchaseOutput execute(AuthorizePurchaseInput input) {
        var authorizationId = new AtomicReference<AuthorizationId>();

        return support.executeIdempotentResponse(
                input.idempotencyKey(),
                "AuthorizePurchase",
                input.creditAccountId(),
                input,
                account -> {
                    AuthorizationId generated = AuthorizationId.of(uniqueIdGenerator.generate());
                    authorizationId.set(generated);
                    return account.authorizePurchase(generated, input.amount(), input.merchantName(), now());
                },
                result -> new AuthorizePurchaseOutput(
                        result.output(),
                        authorizationId.get().value().toString(),
                        result.replayed()
                ),
                AuthorizePurchaseOutput.class
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
