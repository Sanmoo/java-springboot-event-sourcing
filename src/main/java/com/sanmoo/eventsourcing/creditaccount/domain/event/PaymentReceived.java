package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import java.time.Instant;

public record PaymentReceived(CreditAccountId creditAccountId, Money amount, Instant occurredAt) implements CreditAccountEvent {}
