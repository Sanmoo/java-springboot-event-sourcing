package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;

public record CreditAccountBlocked(CreditAccountId creditAccountId, String reason, Instant occurredAt) implements CreditAccountEvent {}
