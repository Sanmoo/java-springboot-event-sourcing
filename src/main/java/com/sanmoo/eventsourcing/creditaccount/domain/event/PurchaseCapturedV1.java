package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;

/** Legacy captured event payloads did not persist an amount; they represented a full capture. */
public record PurchaseCapturedV1(CreditAccountId creditAccountId, AuthorizationId authorizationId, Instant occurredAt) implements CreditAccountEvent {}
