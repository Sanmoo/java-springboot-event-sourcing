package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.ProjectionNotReadyException;
import com.sanmoo.eventsourcing.creditaccount.core.error.SummaryNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.PurchaseAuthorizationOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetCreditAccountUseCase {

    private final CreditAccountSummaryRepository summaries;

    public GetCreditAccountOutput execute(GetCreditAccountInput input) {
        Optional<CreditAccountSummary> maybe = summaries.findById(input.creditAccountId());
        if (maybe.isEmpty()) {
            if (input.minVersion() != null) {
                throw new ProjectionNotReadyException(input.creditAccountId().value(), null, input.minVersion());
            }
            throw new SummaryNotFoundException("Credit account not found: " + input.creditAccountId().value());
        }
        CreditAccountSummary summary = maybe.get();
        if (input.minVersion() != null && summary.projectedVersion() < input.minVersion()) {
            throw new ProjectionNotReadyException(input.creditAccountId().value(), summary.projectedVersion(), input.minVersion());
        }
        return new GetCreditAccountOutput(toOutput(summary));
    }

    private CreditAccountOutput toOutput(CreditAccountSummary s) {
        List<PurchaseAuthorizationOutput> auths = s.authorizations().stream()
                .map(a -> new PurchaseAuthorizationOutput(a.authorizationId().toString(), a.amount(), a.status(), a.merchantName()))
                .toList();
        return new CreditAccountOutput(s.creditAccountId().toString(), s.opened(), s.creditLimit(), s.outstandingBalance(), s.authorizedAmount(), s.availableLimit(), auths, s.projectedVersion());
    }
}
