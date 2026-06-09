package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.OpenCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.OpenCreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;
    private final UniqueIdGenerator uniqueIdGenerator;

    public OpenCreditAccountOutput execute(OpenCreditAccountInput input) {
        CreditAccountId creditAccountId = CreditAccountId.of(uniqueIdGenerator.generate());
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
