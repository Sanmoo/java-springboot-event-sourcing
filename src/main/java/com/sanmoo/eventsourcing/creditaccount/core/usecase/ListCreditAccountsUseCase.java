package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidPageSizeException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ListCreditAccountsInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ListCreditAccountsOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.PurchaseAuthorizationOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListCreditAccountsUseCase {

    private final CreditAccountSummaryRepository summaries;

    public ListCreditAccountsOutput execute(ListCreditAccountsInput input) {
        if (input.size() > ListCreditAccountsInput.MAX_SIZE) {
            throw new InvalidPageSizeException("size must be <= " + ListCreditAccountsInput.MAX_SIZE);
        }
        if (input.page() < 0) {
            throw new InvalidPageSizeException("page must be >= 0");
        }
        CreditAccountSummaryPage page = summaries.findAll(new CreditAccountSummaryPageRequest(input.page(), input.size()));
        List<CreditAccountOutput> items = page.items().stream()
                .map(this::toOutput)
                .toList();
        return new ListCreditAccountsOutput(items, page.page(), page.size(), page.totalItems(), page.totalPages());
    }

    private CreditAccountOutput toOutput(CreditAccountSummary s) {
        List<PurchaseAuthorizationOutput> auths = s.authorizations().stream()
                .map(a -> new PurchaseAuthorizationOutput(a.authorizationId().toString(), a.amount(), a.status(), a.merchantName()))
                .toList();
        return new CreditAccountOutput(
                s.creditAccountId().toString(),
                s.opened(),
                s.creditLimit(),
                s.outstandingBalance(),
                s.authorizedAmount(),
                s.availableLimit(),
                auths,
                s.projectedVersion());
    }
}
