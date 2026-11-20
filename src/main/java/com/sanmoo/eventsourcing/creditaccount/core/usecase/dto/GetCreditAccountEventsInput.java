package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

public record GetCreditAccountEventsInput(CreditAccountId accountId, long fromVersion, int limit) {
}
