package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JdbcEventStoreAdapter implements EventStorePort {

    private static final String LOAD_EVENTS_SQL = """
            SELECT event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, metadata, occurred_at
            FROM event_store
            WHERE aggregate_type = ? AND aggregate_id = ?
            ORDER BY aggregate_version ASC
            """;

    private static final String INSERT_EVENT_SQL = """
            INSERT INTO event_store (event_id, aggregate_id, aggregate_type, aggregate_version, event_type, payload, metadata, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EventTypeMapper eventTypeMapper;
    private final RowMapper<EventEnvelope> rowMapper = this::mapEventEnvelope;

    @Override
    @Transactional(readOnly = true)
    public List<EventEnvelope> loadEvents(String aggregateType, String aggregateId) {
        return jdbcTemplate.query(LOAD_EVENTS_SQL, rowMapper, aggregateType, aggregateId);
    }

    @Override
    @Transactional
    public AppendResult appendEvents(String aggregateType, String aggregateId, long expectedVersion, List<CreditAccountEvent> events, Map<String, String> metadata) {
        try {
            long version = expectedVersion;
            for (CreditAccountEvent event : events) {
                version++;
                UUID eventId = UUID.randomUUID();
                String eventType = eventTypeMapper.eventType(event);
                String payload = eventTypeMapper.serialize(event);
                String metadataJson = serializeMetadata(metadata);
                Instant occurredAt = event.occurredAt();

                jdbcTemplate.update(INSERT_EVENT_SQL,
                        eventId,
                        aggregateId,
                        aggregateType,
                        version,
                        eventType,
                        payload,
                        metadataJson,
                        Timestamp.from(occurredAt));
            }
            return new AppendResult(version);
        } catch (DataIntegrityViolationException e) {
            throw new ConcurrencyConflictException(aggregateType, aggregateId, expectedVersion, e);
        }
    }

    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return eventTypeMapper.serializeMetadata(metadata);
    }

    private EventEnvelope mapEventEnvelope(ResultSet rs, int rowNum) throws SQLException {
        UUID eventId = rs.getObject("event_id", UUID.class);
        String aggregateType = rs.getString("aggregate_type");
        String aggregateId = rs.getString("aggregate_id");
        long aggregateVersion = rs.getLong("aggregate_version");
        String eventType = rs.getString("event_type");
        String payload = rs.getString("payload");
        String metadataJson = rs.getString("metadata");
        Timestamp occurredAtTs = rs.getTimestamp("occurred_at");
        Instant occurredAt = occurredAtTs.toInstant();

        CreditAccountEvent event = eventTypeMapper.deserialize(eventType, payload);
        Map<String, String> metadata = deserializeMetadata(metadataJson);

        return new EventEnvelope(eventId, aggregateType, aggregateId, aggregateVersion, event, occurredAt, metadata);
    }

    private Map<String, String> deserializeMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        return eventTypeMapper.deserializeMetadata(metadataJson);
    }
}
