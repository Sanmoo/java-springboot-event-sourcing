package com.sanmoo.eventsourcing.creditaccount.concurrency;

import java.util.List;

public record ConcurrentCommandResult<T>(
    List<T> successfulResults, int conflictCount, int failureCount, int retryCount) {}
