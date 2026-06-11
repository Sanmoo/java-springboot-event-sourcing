package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidPageSizeException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ListCreditAccountsInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ListCreditAccountsOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        return new ListCreditAccountsOutput(page);
    }
}
