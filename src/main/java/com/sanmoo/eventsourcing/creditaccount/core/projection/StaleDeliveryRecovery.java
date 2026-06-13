package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxDeliveryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.ProjectionConfig;
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StaleDeliveryRecovery {

    private final OutboxDeliveryRepository deliveries;
    private final ProjectionConfig properties;
    private final TransactionRunner transactionRunner;

    public int recover() {
        return transactionRunner.runInTransaction(() ->
                deliveries.recoverStaleProcessing(properties.getProcessingTimeout(), 100));
    }
}
