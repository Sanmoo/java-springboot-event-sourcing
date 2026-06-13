package com.sanmoo.eventsourcing.creditaccount.adapter.in.scheduler;

import com.sanmoo.eventsourcing.creditaccount.core.port.ProjectionConfig;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorkerResult;
import com.sanmoo.eventsourcing.creditaccount.core.projection.StaleDeliveryRecovery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.log.LogAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "credit-account.projections", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxProjectionWorkerRunner {

    private final LogAccessor log = new LogAccessor(OutboxProjectionWorkerRunner.class);

    private final ProjectionWorker worker;
    private final StaleDeliveryRecovery recovery;
    private final int batchSize;
    private final int staleRecoveryIntervalCycles;

    private int cycle;

    public OutboxProjectionWorkerRunner(ProjectionWorker worker,
                                        StaleDeliveryRecovery recovery,
                                        ProjectionConfig properties,
                                        @org.springframework.beans.factory.annotation.Value(
                                                "${credit-account.projections.stale-recovery-interval-cycles:30}")
                                        int staleRecoveryIntervalCycles) {
        this.worker = worker;
        this.recovery = recovery;
        this.batchSize = properties.getBatchSize();
        this.staleRecoveryIntervalCycles = staleRecoveryIntervalCycles;
    }

    @Scheduled(fixedDelayString = "${credit-account.projections.poll-interval:1s}")
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void run() {
        try {
            ProjectionWorkerResult result = worker.processOnce(batchSize);
            if (result.claimed() > 0) {
                log.debug(() -> "Projection worker claimed " + result.claimed()
                        + " processed=" + result.processed()
                        + " blocked=" + result.blocked()
                        + " retried=" + result.retried()
                        + " failed=" + result.failed());
            }
            cycle++;
            if (staleRecoveryIntervalCycles > 0 && cycle % staleRecoveryIntervalCycles == 0) {
                int recovered = recovery.recover();
                if (recovered > 0) {
                    log.info(() -> "Recovered " + recovered + " stale outbox deliveries");
                }
            }
        } catch (RuntimeException e) {
            log.error(e, "Projection worker tick failed");
        }
    }
}
