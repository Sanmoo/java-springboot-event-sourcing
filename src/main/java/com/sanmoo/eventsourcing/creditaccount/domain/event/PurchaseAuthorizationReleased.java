package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import java.time.Instant;

public record PurchaseAuthorizationReleased(CreditAccountId creditAccountId, AuthorizationId authorizationId, Money amount, Instant occurredAt) implements CreditAccountEvent {}
