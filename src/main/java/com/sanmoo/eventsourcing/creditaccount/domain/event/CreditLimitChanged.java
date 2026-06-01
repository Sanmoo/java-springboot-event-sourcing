package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import java.time.Instant;

public record CreditLimitChanged(CreditAccountId creditAccountId, Money newLimit, Instant occurredAt) implements CreditAccountEvent {}
