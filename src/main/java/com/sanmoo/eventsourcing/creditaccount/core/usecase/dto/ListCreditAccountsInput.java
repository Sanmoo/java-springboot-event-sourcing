package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record ListCreditAccountsInput(int page, int size, String status) {
    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public ListCreditAccountsInput(int page, int size) {
        this(page, size, null);
    }

    public ListCreditAccountsInput {
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
    }
}
