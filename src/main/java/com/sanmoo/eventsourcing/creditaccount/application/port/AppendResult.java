package com.sanmoo.eventsourcing.creditaccount.application.port;

public record AppendResult(long newAggregateVersion) {}
