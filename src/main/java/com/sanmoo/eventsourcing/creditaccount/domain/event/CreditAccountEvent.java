package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;

public sealed interface CreditAccountEvent permits CreditAccountOpened, CreditAccountBlocked, CreditAccountUnblocked, CreditLimitAssigned, CreditLimitChanged, PurchaseAuthorized, PurchaseCapturedV1, PurchaseCapturedV2, PurchaseAuthorizationReleased, PaymentReceived {
    CreditAccountId creditAccountId();
    Instant occurredAt();
}
