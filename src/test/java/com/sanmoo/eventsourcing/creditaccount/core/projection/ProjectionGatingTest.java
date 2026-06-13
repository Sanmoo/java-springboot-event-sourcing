package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.ProjectionCheckpoint;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionGatingTest {

    private static final String PROJECTION = "credit-account-summary-projector";
    private final UUID aggregateId = UUID.randomUUID();

    private OutboxEvent event(long version) {
        CreditAccountOpened opened = new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now());
        return new OutboxEvent(UUID.randomUUID(), "CreditAccount", aggregateId.toString(), version,
                "CreditAccountOpened", opened, java.util.Map.of(), Instant.now());
    }

    private ProjectionCheckpoint checkpoint(long last) {
        return new ProjectionCheckpoint(PROJECTION, "CreditAccount", aggregateId.toString(), last,
                UUID.randomUUID(), Instant.now());
    }

    @Test
    void noCheckpoint_firstVersionApplies() {
        var result = new ProjectionGating().decide(PROJECTION, event(1L), Optional.empty());
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.APPLY);
    }

    @Test
    void noCheckpoint_nonFirstVersionIsBlocked() {
        var result = new ProjectionGating().decide(PROJECTION, event(2L), Optional.empty());
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.BLOCKED);
        assertThat(result.reason()).contains("expected version 1 but got 2");
    }

    @Test
    void checkpoint_expectedNextApplies() {
        var result = new ProjectionGating().decide(PROJECTION, event(3L), Optional.of(checkpoint(2L)));
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.APPLY);
    }

    @Test
    void checkpoint_alreadyApplied() {
        var result = new ProjectionGating().decide(PROJECTION, event(2L), Optional.of(checkpoint(3L)));
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.ALREADY_APPLIED);
    }

    @Test
    void checkpoint_futureVersionIsBlocked() {
        var result = new ProjectionGating().decide(PROJECTION, event(5L), Optional.of(checkpoint(3L)));
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.BLOCKED);
        assertThat(result.reason()).contains("expected version 4 but got 5");
    }

    @Test
    void invalidVersionIsPermanentFailure() {
        var result = new ProjectionGating().decide(PROJECTION, event(0L), Optional.empty());
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.PERMANENT_FAILURE);
    }
}
