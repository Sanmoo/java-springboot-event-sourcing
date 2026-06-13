package com.sanmoo.eventsourcing.creditaccount.core.projection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionWorkerResultTest {

    @Test
    void empty_hasAllCountersZero() {
        var result = ProjectionWorkerResult.empty();
        assertThat(result.claimed()).isZero();
        assertThat(result.processed()).isZero();
        assertThat(result.blocked()).isZero();
        assertThat(result.retried()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void withClaimed_replacesClaimedOnly() {
        var result = ProjectionWorkerResult.empty().withClaimed(3);
        assertThat(result.claimed()).isEqualTo(3);
        assertThat(result.processed()).isZero();
    }

    @Test
    void plusCounters_accumulateIndependently() {
        var result = ProjectionWorkerResult.empty()
                .withClaimed(2)
                .plusProcessed(1)
                .plusBlocked(1)
                .plusRetried(1)
                .plusFailed(1);
        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.blocked()).isEqualTo(1);
        assertThat(result.retried()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }
}
