package com.sanmoo.eventsourcing.creditaccount.core.projection;

public enum ConsumerNames {
    CREDIT_ACCOUNT_SUMMARY_PROJECTOR("credit-account-summary-projector");

    private final String name;

    ConsumerNames(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
