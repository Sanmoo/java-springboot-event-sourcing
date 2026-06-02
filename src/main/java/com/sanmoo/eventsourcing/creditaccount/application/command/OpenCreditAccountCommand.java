package com.sanmoo.eventsourcing.creditaccount.application.command;

public record OpenCreditAccountCommand(String idempotencyKey) {}
