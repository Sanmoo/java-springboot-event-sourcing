package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.port.ProjectionCheckpointRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.ProjectionCheckpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JdbcProjectionCheckpointRepository implements ProjectionCheckpointRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO projection_checkpoints
              (projection_name, aggregate_type, aggregate_id, last_projected_version, last_event_id, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (projection_name, aggregate_type, aggregate_id) DO UPDATE SET
              last_projected_version = EXCLUDED.last_projected_version,
              last_event_id = EXCLUDED.last_event_id,
              updated_at = EXCLUDED.updated_at
            """;

    private static final String FIND_SQL = """
            SELECT projection_name, aggregate_type, aggregate_id, last_projected_version, last_event_id, updated_at
            FROM projection_checkpoints
            WHERE projection_name = ? AND aggregate_type = ? AND aggregate_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void upsert(ProjectionCheckpoint checkpoint) {
        jdbcTemplate.update(UPSERT_SQL,
                checkpoint.projectionName(),
                checkpoint.aggregateType(),
                checkpoint.aggregateId(),
                checkpoint.lastProjectedVersion(),
                checkpoint.lastEventId(),
                Timestamp.from(checkpoint.updatedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectionCheckpoint> find(String projectionName, String aggregateType, String aggregateId) {
        try {
            ProjectionCheckpoint checkpoint = jdbcTemplate.queryForObject(FIND_SQL, (rs, rowNum) -> new ProjectionCheckpoint(
                    rs.getString("projection_name"),
                    rs.getString("aggregate_type"),
                    rs.getString("aggregate_id"),
                    rs.getLong("last_projected_version"),
                    rs.getObject("last_event_id", UUID.class),
                    toInstant(rs.getTimestamp("updated_at"))
            ), projectionName, aggregateType, aggregateId);
            return Optional.ofNullable(checkpoint);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
