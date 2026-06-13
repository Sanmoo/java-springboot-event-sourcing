package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxDeliveryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxDelivery;
import com.sanmoo.eventsourcing.creditaccount.core.projection.OutboxDeliveryStatus;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JdbcOutboxDeliveryRepository implements OutboxDeliveryRepository {

    private static final String INSERT_DELIVERY_SQL = """
            INSERT INTO outbox_deliveries
              (event_id, consumer_name, status, processing_attempts, max_attempts, next_attempt_at, created_at, updated_at)
            VALUES (?, ?, 'PENDING', 0, ?, now(), now(), now())
            ON CONFLICT (event_id, consumer_name) DO NOTHING
            """;

    private static final String CLAIM_PENDING_SQL = """
            WITH claimable AS (
                SELECT d.event_id, d.consumer_name
                FROM outbox_deliveries d
                JOIN outbox_events e ON e.event_id = d.event_id
                WHERE d.consumer_name = ?
                  AND d.status = 'PENDING'
                  AND d.next_attempt_at <= now()
                ORDER BY e.aggregate_type, e.aggregate_id, e.aggregate_version
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE outbox_deliveries d
            SET status = 'PROCESSING',
                locked_at = now(),
                locked_by = ?,
                updated_at = now()
            FROM claimable c
            WHERE d.event_id = c.event_id
              AND d.consumer_name = c.consumer_name
            RETURNING d.event_id, d.consumer_name, d.status, d.processing_attempts, d.max_attempts,
                      d.next_attempt_at, d.locked_at, d.locked_by, d.last_error, d.blocked_reason,
                      d.blocked_at, d.processed_at, d.failed_at, d.created_at, d.updated_at
            """;

    private static final String CLAIM_NEXT_FOR_AGGREGATE_SQL = """
            WITH target AS (
                SELECT d.event_id, d.consumer_name
                FROM outbox_deliveries d
                JOIN outbox_events e ON e.event_id = d.event_id
                WHERE d.consumer_name = ?
                  AND e.aggregate_type = ?
                  AND e.aggregate_id = ?
                  AND e.aggregate_version = ?
                  AND d.status = 'PENDING'
                  AND d.next_attempt_at <= now()
                FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_deliveries d
            SET status = 'PROCESSING',
                locked_at = now(),
                locked_by = ?,
                updated_at = now()
            FROM target c
            WHERE d.event_id = c.event_id
              AND d.consumer_name = c.consumer_name
            RETURNING d.event_id, d.consumer_name, d.status, d.processing_attempts, d.max_attempts,
                      d.next_attempt_at, d.locked_at, d.locked_by, d.last_error, d.blocked_reason,
                      d.blocked_at, d.processed_at, d.failed_at, d.created_at, d.updated_at
            """;

    private static final String MARK_PROCESSED_SQL = """
            UPDATE outbox_deliveries
            SET status = 'PROCESSED', processed_at = now(), locked_by = null, locked_at = null,
                blocked_reason = null, blocked_at = null, last_error = null, updated_at = now()
            WHERE event_id = ? AND consumer_name = ?
            """;

    private static final String MARK_BLOCKED_SQL = """
            UPDATE outbox_deliveries
            SET status = 'BLOCKED', blocked_reason = ?, blocked_at = now(),
                locked_by = null, locked_at = null, last_error = null, updated_at = now()
            WHERE event_id = ? AND consumer_name = ?
            """;

    private static final String MARK_RETRY_SQL = """
            UPDATE outbox_deliveries
            SET status = 'PENDING', processing_attempts = ?, next_attempt_at = ?, last_error = ?,
                locked_by = null, locked_at = null, updated_at = now()
            WHERE event_id = ? AND consumer_name = ?
            """;

    private static final String MARK_FAILED_SQL = """
            UPDATE outbox_deliveries
            SET status = 'FAILED', processing_attempts = ?, failed_at = now(), last_error = ?,
                locked_by = null, locked_at = null, updated_at = now()
            WHERE event_id = ? AND consumer_name = ?
            """;

    private static final String UNBLOCK_NEXT_SQL = """
            UPDATE outbox_deliveries d
            SET status = 'PENDING', blocked_reason = null, blocked_at = null,
                next_attempt_at = now(), locked_by = null, locked_at = null, updated_at = now()
            FROM outbox_events e
            WHERE d.event_id = e.event_id
              AND d.consumer_name = ?
              AND d.status = 'BLOCKED'
              AND e.aggregate_type = ?
              AND e.aggregate_id = ?
              AND e.aggregate_version = ?
            """;

    private static final String FIND_STALE_SQL = """
            SELECT event_id, consumer_name, status, processing_attempts, max_attempts, next_attempt_at,
                   locked_at, locked_by, last_error, blocked_reason, blocked_at, processed_at, failed_at,
                   created_at, updated_at
            FROM outbox_deliveries
            WHERE status = 'PROCESSING' AND locked_at < now() - (?::interval)
            ORDER BY locked_at ASC
            LIMIT ?
            """;

    private static final String RECOVER_STALE_SQL = """
            UPDATE outbox_deliveries
            SET status = 'PENDING', locked_by = null, locked_at = null,
                next_attempt_at = now(), last_error = ?, updated_at = now()
            WHERE status = 'PROCESSING' AND locked_at < now() - (?::interval)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public List<OutboxDelivery> claimPending(String consumerName, String workerId, int batchSize) {
        return jdbcTemplate.query(CLAIM_PENDING_SQL, (rs, rowNum) -> map(rs),
                consumerName, batchSize, workerId);
    }

    @Override
    @Transactional
    public Optional<OutboxDelivery> claimNextForAggregate(String consumerName, String workerId,
                                                           String aggregateType, String aggregateId,
                                                           long expectedVersion) {
        List<OutboxDelivery> result = jdbcTemplate.query(CLAIM_NEXT_FOR_AGGREGATE_SQL, (rs, rowNum) -> map(rs),
                consumerName, aggregateType, aggregateId, expectedVersion, workerId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    @Transactional
    public void markProcessed(UUID eventId, String consumerName) {
        jdbcTemplate.update(MARK_PROCESSED_SQL, eventId, consumerName);
    }

    @Override
    @Transactional
    public void markBlocked(UUID eventId, String consumerName, String reason) {
        jdbcTemplate.update(MARK_BLOCKED_SQL, truncate(reason), eventId, consumerName);
    }

    @Override
    @Transactional
    public void markRetryableFailure(UUID eventId, String consumerName, int newAttempts, int maxAttempts,
                                     String error, Duration backoff) {
        Instant nextAttempt = Instant.now().plus(backoff);
        jdbcTemplate.update(MARK_RETRY_SQL, newAttempts, Timestamp.from(nextAttempt), truncate(error),
                eventId, consumerName);
    }

    @Override
    @Transactional
    public void markPermanentFailure(UUID eventId, String consumerName, int attempts, String error) {
        jdbcTemplate.update(MARK_FAILED_SQL, attempts, truncate(error), eventId, consumerName);
    }

    @Override
    @Transactional
    public void unblockNextVersion(String consumerName, String aggregateType, String aggregateId, long version) {
        jdbcTemplate.update(UNBLOCK_NEXT_SQL, consumerName, aggregateType, aggregateId, version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxDelivery> findStaleProcessing(Duration timeout, int limit) {
        return jdbcTemplate.query(FIND_STALE_SQL, (rs, rowNum) -> map(rs), intervalLiteral(timeout), limit);
    }

    @Override
    @Transactional
    public int recoverStaleProcessing(Duration timeout, int limit) {
        return jdbcTemplate.update(RECOVER_STALE_SQL,
                "Recovered stale PROCESSING lock", intervalLiteral(timeout));
    }

    @Override
    @Transactional
    public int insertDeliveriesForEvent(OutboxEvent event, int defaultMaxAttempts) {
        return jdbcTemplate.update(INSERT_DELIVERY_SQL,
                event.eventId(),
                "credit-account-summary-projector",
                defaultMaxAttempts);
    }

    private String intervalLiteral(Duration timeout) {
        return (int) timeout.getSeconds() + " seconds";
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private OutboxDelivery map(ResultSet rs) throws SQLException {
        return new OutboxDelivery(
                rs.getObject("event_id", UUID.class),
                rs.getString("consumer_name"),
                OutboxDeliveryStatus.valueOf(rs.getString("status")),
                rs.getInt("processing_attempts"),
                rs.getInt("max_attempts"),
                rs.getTimestamp("next_attempt_at").toInstant(),
                toInstant(rs.getTimestamp("locked_at")),
                rs.getString("locked_by"),
                rs.getString("last_error"),
                rs.getString("blocked_reason"),
                toInstant(rs.getTimestamp("blocked_at")),
                toInstant(rs.getTimestamp("processed_at")),
                toInstant(rs.getTimestamp("failed_at")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private OutboxDeliveryStatus parseStatus(String status) {
        return OutboxDeliveryStatus.valueOf(status);
    }
}
