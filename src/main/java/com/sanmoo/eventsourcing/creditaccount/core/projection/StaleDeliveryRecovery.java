package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxDeliveryRepository;
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class StaleDeliveryRecovery {

    private final OutboxDeliveryRepository deliveries;
    private final ProjectionProperties properties;
    private final PlatformTransactionManager transactionManager;

    public int recover() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status ->
                deliveries.recoverStaleProcessing(properties.getProcessingTimeout(), 100));
    }
}
