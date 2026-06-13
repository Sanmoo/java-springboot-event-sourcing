package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.ProjectionCheckpoint;

import java.util.Optional;

public interface ProjectionCheckpointRepository {
    Optional<ProjectionCheckpoint> find(String projectionName, String aggregateType, String aggregateId);

    void upsert(ProjectionCheckpoint checkpoint);
}
