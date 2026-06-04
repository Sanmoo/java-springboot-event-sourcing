package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.UUID;

public interface UniqueIdGenerator {
    UUID generate();
}
