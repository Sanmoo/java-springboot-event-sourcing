package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

import java.util.Optional;

public interface CreditAccountSummaryRepository {
    Optional<CreditAccountSummary> findById(CreditAccountId creditAccountId);
    void upsert(CreditAccountSummary summary);
    CreditAccountSummaryPage findAll(CreditAccountSummaryPageRequest request);
}
