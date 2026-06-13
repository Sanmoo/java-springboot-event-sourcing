package com.sanmoo.eventsourcing.creditaccount.adapter.in.scheduler;

import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorkerResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.log.LogAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "credit-account.projections", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxProjectionWorkerRunner {

    private final LogAccessor log = new LogAccessor(OutboxProjectionWorkerRunner.class);

    private final ProjectionWorker worker;

    private final int batchSize;

    public OutboxProjectionWorkerRunner(ProjectionWorker worker,
                                        @Value("${credit-account.projections.batch-size:50}") int batchSize) {
        this.worker = worker;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${credit-account.projections.poll-interval:1s}")
    public void run() {
        try {
            ProjectionWorkerResult result = worker.processOnce(batchSize);
            if (result.processed() > 0) {
                log.debug(() -> "Projection worker processed " + result.processed()
                        + " events (claimed=" + result.claimed()
                        + ", blocked=" + result.blocked()
                        + ", retried=" + result.retried()
                        + ", failed=" + result.failed() + ")");
            }
        } catch (RuntimeException e) {
            log.error(e, "Projection worker tick failed");
        }
    }
}
