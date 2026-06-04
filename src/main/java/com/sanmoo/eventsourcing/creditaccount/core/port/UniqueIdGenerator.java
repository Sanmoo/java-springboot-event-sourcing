package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.UUID;

@FunctionalInterface
public interface UniqueIdGenerator {
    UUID generate();
}
