package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import org.springframework.stereotype.Service;

@Service
public class GetCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;

    public GetCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public GetCreditAccountOutput execute(GetCreditAccountInput input) {
        CreditAccountOutput output = support.loadAccountOutput(input.creditAccountId());
        return new GetCreditAccountOutput(output);
    }
}
