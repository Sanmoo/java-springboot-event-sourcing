package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CapturePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CapturePurchaseOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CapturePurchaseUseCase {

    private final CreditAccountUseCaseSupport support;

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
