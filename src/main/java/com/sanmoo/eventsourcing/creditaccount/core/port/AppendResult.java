package com.sanmoo.eventsourcing.creditaccount.core.port;

public record AppendResult(long newAggregateVersion) {}
