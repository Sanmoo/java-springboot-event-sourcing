package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import java.time.Instant;

public record PurchaseAuthorized(CreditAccountId creditAccountId, AuthorizationId authorizationId, Money amount, String merchantName, Instant occurredAt) implements CreditAccountEvent {}
