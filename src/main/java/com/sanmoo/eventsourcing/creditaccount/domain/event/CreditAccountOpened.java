package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;

public record CreditAccountOpened(CreditAccountId creditAccountId, Instant occurredAt) implements CreditAccountEvent {}
