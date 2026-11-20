package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.UnblockCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.UnblockCreditAccountOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UnblockCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;

    public UnblockCreditAccountOutput execute(UnblockCreditAccountInput input) {
        return support.executeIdempotent(input.idempotencyKey(), "UnblockCreditAccount", input.creditAccountId(), input,
                account -> account.unblock(input.reason(), Instant.now()),
                result -> new UnblockCreditAccountOutput(result.output(), result.replayed()));
    }
}
