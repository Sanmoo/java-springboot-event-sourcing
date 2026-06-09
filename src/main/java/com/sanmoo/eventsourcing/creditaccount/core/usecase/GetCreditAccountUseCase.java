package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;

    public GetCreditAccountOutput execute(GetCreditAccountInput input) {
        CreditAccountOutput output = support.loadAccountOutput(input.creditAccountId());
        return new GetCreditAccountOutput(output);
    }
}
