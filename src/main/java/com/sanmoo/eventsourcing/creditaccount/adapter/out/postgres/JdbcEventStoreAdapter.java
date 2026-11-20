package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AccountEvent;
import com.sanmoo.eventsourcing.creditaccount.core.port.AccountEventPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStore;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxDeliveryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcEventStoreAdapter implements EventStore {

    private static final String LOAD_EVENTS_SQL = """
            SELECT event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, metadata, occurred_at
            FROM event_store
            WHERE aggregate_type = ? AND aggregate_id = ?
            ORDER BY aggregate_version ASC
            """;

    private static final String READ_ACCOUNT_EVENTS_SQL = """
            SELECT aggregate_version, event_type, occurred_at, payload::text AS payload
            FROM event_store
            WHERE aggregate_type = 'CreditAccount' AND aggregate_id = ? AND aggregate_version >= ?
            ORDER BY aggregate_version ASC
            LIMIT ?
            """;

    private static final String ACCOUNT_EXISTS_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM event_store WHERE aggregate_type = 'CreditAccount' AND aggregate_id = ?
            )
            """;

    private static final String INSERT_EVENT_SQL = """
            INSERT INTO event_store (event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, metadata, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            """;

    private static final String INSERT_OUTBOX_SQL = """
            INSERT INTO outbox_events
              (event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, metadata, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EventTypeMapper eventTypeMapper;
    private final UniqueIdGenerator uniqueIdGenerator;
    private final OutboxDeliveryRepository deliveryRepository;
    private final int defaultMaxAttempts;
    private final RowMapper<EventEnvelope> rowMapper = (rs, rowNum) -> mapEventEnvelope(rs);

    public JdbcEventStoreAdapter(JdbcTemplate jdbcTemplate,
                                  EventTypeMapper eventTypeMapper,
                                  UniqueIdGenerator uniqueIdGenerator,
                                  OutboxDeliveryRepository deliveryRepository,
                                  @Value("${credit-account.projections.max-attempts:10}") int defaultMaxAttempts) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventTypeMapper = eventTypeMapper;
        this.uniqueIdGenerator = uniqueIdGenerator;
        this.deliveryRepository = deliveryRepository;
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventEnvelope> loadEvents(String aggregateType, String aggregateId) {
        return jdbcTemplate.query(LOAD_EVENTS_SQL, rowMapper, aggregateType, aggregateId);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountEventPage readAccountEvents(UUID accountId, long fromVersion, int limit) {
        List<AccountEvent> rows = jdbcTemplate.query(READ_ACCOUNT_EVENTS_SQL, (rs, rowNum) ->
                new AccountEvent(
                        rs.getLong("aggregate_version"),
                        rs.getString("event_type"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        eventTypeMapper.deserializePayload(rs.getString("payload"))),
                accountId.toString(), fromVersion, limit + 1);
        boolean accountExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                ACCOUNT_EXISTS_SQL, Boolean.class, accountId.toString()));
        Long nextFromVersion = null;
        if (rows.size() > limit) {
            rows = new ArrayList<>(rows.subList(0, limit));
            nextFromVersion = rows.getLast().version() + 1;
        }
        return new AccountEventPage(rows, nextFromVersion, Boolean.TRUE.equals(accountExists));
    }

    @Override
    @Transactional
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public AppendResult appendEvents(String aggregateType, String aggregateId, long expectedVersion, List<CreditAccountEvent> events, Map<String, String> metadata) {
        try {
            long version = expectedVersion;
            for (CreditAccountEvent event : events) {
                version++;
                UUID eventId = uniqueIdGenerator.generate();
                String eventType = eventTypeMapper.eventType(event);
                String payload = eventTypeMapper.serialize(event);
                String metadataJson = serializeMetadata(metadata);
                Instant occurredAt = event.occurredAt();

                insertEventRow(INSERT_EVENT_SQL, eventId, aggregateType, aggregateId, version, eventType, payload, metadataJson, occurredAt);
                insertEventRow(INSERT_OUTBOX_SQL, eventId, aggregateType, aggregateId, version, eventType, payload, metadataJson, occurredAt);

                OutboxEvent outboxEvent = new OutboxEvent(eventId, aggregateType, aggregateId, version, eventType, event, metadata, occurredAt);
                deliveryRepository.insertDeliveriesForEvent(outboxEvent, defaultMaxAttempts);
            }
            return new AppendResult(version);
        } catch (DataIntegrityViolationException e) {
            throw new ConcurrencyConflictException(aggregateType, aggregateId, expectedVersion, e);
        }
    }

    private void insertEventRow(String sql, UUID eventId, String aggregateType,
                                String aggregateId, long version, String eventType,
                                String payload, String metadataJson, Instant occurredAt) {
        jdbcTemplate.update(sql, eventId, aggregateType, aggregateId, version,
                            eventType, payload, metadataJson, Timestamp.from(occurredAt));
    }

    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        return eventTypeMapper.serializeMetadata(metadata);
    }

    private EventEnvelope mapEventEnvelope(ResultSet rs) throws SQLException {
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
