package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventLoader;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JdbcOutboxEventAdapter implements OutboxEventRepository, OutboxEventLoader {

    private static final String FIND_PENDING_SQL = """
            SELECT event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, metadata, occurred_at
            FROM outbox_events
            WHERE processed_at IS NULL
            ORDER BY occurred_at ASC, event_id ASC
            LIMIT ?
            """;

    private static final String MARK_PROCESSED_SQL =
            "UPDATE outbox_events SET processed_at = ? WHERE event_id = ?";

    private static final String MARK_FAILED_SQL =
            "UPDATE outbox_events SET processing_attempts = processing_attempts + 1, last_error = ? WHERE event_id = ?";

    private static final String FIND_BY_ID_SQL = """
            SELECT event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, metadata, occurred_at
            FROM outbox_events
            WHERE event_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EventTypeMapper eventTypeMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboxEvent> findById(UUID eventId) {
        return jdbcTemplate.query(FIND_BY_ID_SQL, (rs, rowNum) -> map(rs), eventId)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findPending(int limit) {
        return jdbcTemplate.query(FIND_PENDING_SQL, (rs, rowNum) -> map(rs), limit);
    }

    @Override
    @Transactional
    public void markProcessed(UUID eventId) {
        jdbcTemplate.update(MARK_PROCESSED_SQL, Timestamp.from(Instant.now()), eventId);
    }

    @Override
    @Transactional
    public void markFailed(UUID eventId, String error) {
        jdbcTemplate.update(MARK_FAILED_SQL, truncate(error), eventId);
    }

    private OutboxEvent map(ResultSet rs) throws SQLException {
        UUID eventId = rs.getObject("event_id", UUID.class);
        String aggregateType = rs.getString("aggregate_type");
        String aggregateId = rs.getString("aggregate_id");
        long aggregateVersion = rs.getLong("aggregate_version");
        String eventType = rs.getString("event_type");
        String payload = rs.getString("payload");
        String metadataJson = rs.getString("metadata");
        Timestamp occurredAt = rs.getTimestamp("occurred_at");

        var event = eventTypeMapper.deserialize(eventType, payload);
        Map<String, String> metadata = deserializeMetadata(metadataJson);
        return new OutboxEvent(eventId, aggregateType, aggregateId, aggregateVersion, eventType, event, metadata, occurredAt.toInstant());
    }

    private Map<String, String> deserializeMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        return eventTypeMapper.deserializeMetadata(metadataJson);
    }

    private String truncate(String error) {
        if (error == null) return null;
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }
}
