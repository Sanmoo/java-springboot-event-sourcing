package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ChangeCreditLimitInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ChangeCreditLimitOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ChangeCreditLimitUseCase {

    private final CreditAccountUseCaseSupport support;

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
