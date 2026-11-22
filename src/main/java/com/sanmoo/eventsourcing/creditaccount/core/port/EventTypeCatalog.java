package com.sanmoo.eventsourcing.creditaccount.core.port;

public interface EventTypeCatalog {
    boolean isKnown(String eventType);
}
