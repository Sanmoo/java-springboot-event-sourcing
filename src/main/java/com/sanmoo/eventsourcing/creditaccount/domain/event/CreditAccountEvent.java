package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;

public sealed interface CreditAccountEvent permits CreditAccountOpened, CreditLimitAssigned, CreditLimitChanged, PurchaseAuthorized, PurchaseCaptured, PurchaseAuthorizationReleased, PaymentReceived {
    CreditAccountId creditAccountId();
    Instant occurredAt();
}
