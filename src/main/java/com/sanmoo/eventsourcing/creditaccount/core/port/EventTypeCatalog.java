package com.sanmoo.eventsourcing.creditaccount.core.port;

@FunctionalInterface
public interface EventTypeCatalog {
    boolean isKnown(String eventType);
}
