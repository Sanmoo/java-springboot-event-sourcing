package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;

public record ListCreditAccountsOutput(CreditAccountSummaryPage page) {}
