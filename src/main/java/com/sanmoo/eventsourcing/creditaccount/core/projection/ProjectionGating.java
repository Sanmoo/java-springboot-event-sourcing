package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.ProjectionCheckpoint;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProjectionGating {

    public ProjectionGatingResult decide(String projectionName, OutboxEvent event, Optional<ProjectionCheckpoint> checkpoint) {
        if (event.aggregateVersion() < 1L) {
            return ProjectionGatingResult.permanentFailure(
                    "Invalid aggregate version: " + event.aggregateVersion());
        }

        long expected;
        if (checkpoint.isPresent()) {
            expected = checkpoint.get().lastProjectedVersion() + 1L;
        } else {
            expected = 1L;
        }

        if (event.aggregateVersion() == expected) {
            return ProjectionGatingResult.apply();
        }

        if (checkpoint.isPresent() && event.aggregateVersion() <= checkpoint.get().lastProjectedVersion()) {
            return ProjectionGatingResult.alreadyApplied();
        }

        return ProjectionGatingResult.blocked(
                "Projection gap: expected version " + expected + " but got " + event.aggregateVersion());
    }
}
