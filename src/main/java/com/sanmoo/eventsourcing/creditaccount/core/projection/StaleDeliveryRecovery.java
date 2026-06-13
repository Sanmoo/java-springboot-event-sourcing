package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxDeliveryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StaleDeliveryRecovery {

    private final OutboxDeliveryRepository deliveries;
    private final ProjectionProperties properties;
    private final TransactionRunner transactionRunner;

    public int recover() {
        return transactionRunner.runInTransaction(() ->
                deliveries.recoverStaleProcessing(properties.getProcessingTimeout(), 100));
    }
}
