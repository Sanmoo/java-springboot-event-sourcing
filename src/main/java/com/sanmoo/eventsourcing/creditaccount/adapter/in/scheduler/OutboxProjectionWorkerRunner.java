package com.sanmoo.eventsourcing.creditaccount.adapter.in.scheduler;

import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "credit-account.projections", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OutboxProjectionWorkerRunner {

    private final ProjectionWorker worker;

    @Scheduled(fixedDelayString = "${credit-account.projections.poll-interval:1s}")
    public void run() {
        try {
            int processed = worker.processOnce();
            if (processed > 0) {
                log.debug("Projection worker processed {} events", processed);
            }
        } catch (RuntimeException e) {
            log.error("Projection worker tick failed", e);
        }
    }
}
