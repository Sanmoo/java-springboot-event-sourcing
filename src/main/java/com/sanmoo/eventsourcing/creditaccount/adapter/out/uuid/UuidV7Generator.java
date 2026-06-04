package com.sanmoo.eventsourcing.creditaccount.adapter.out.uuid;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidV7Generator implements UniqueIdGenerator {
    @Override
    public UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
