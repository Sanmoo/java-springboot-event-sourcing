package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.BlockCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.BlockCreditAccountOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BlockCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;

    public BlockCreditAccountOutput execute(BlockCreditAccountInput input) {
        return support.executeIdempotent(input.idempotencyKey(), "BlockCreditAccount", input.creditAccountId(), input,
                account -> account.block(input.reason(), Instant.now()),
                result -> new BlockCreditAccountOutput(result.output(), result.replayed()));
    }
}
