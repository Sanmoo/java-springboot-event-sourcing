package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record OpenCreditAccountInput(String idempotencyKey) {}
