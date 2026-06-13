package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.function.Supplier;

@FunctionalInterface
public interface TransactionRunner {
    <T> T runInTransaction(Supplier<T> action);
}
