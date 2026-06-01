package com.sanmoo.eventsourcing.creditaccount.domain.model;

public record PurchaseAuthorization(AuthorizationId id, Money amount, PurchaseAuthorizationStatus status, String merchantName) {
    public PurchaseAuthorization capture() {
        return new PurchaseAuthorization(id, amount, PurchaseAuthorizationStatus.CAPTURED, merchantName);
    }

    public PurchaseAuthorization release() {
        return new PurchaseAuthorization(id, amount, PurchaseAuthorizationStatus.RELEASED, merchantName);
    }
}
