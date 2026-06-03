package com.sanmoo.eventsourcing.creditaccount.core.usecase;

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
