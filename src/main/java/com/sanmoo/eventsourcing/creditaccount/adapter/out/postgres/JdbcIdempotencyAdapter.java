package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class JdbcIdempotencyAdapter implements IdempotencyPort {

    private static final String INSERT_STARTED_SQL = """
            INSERT INTO idempotency_records (idempotency_key, command_type, aggregate_id, request_hash, status, created_at)
            VALUES (?, ?, ?, ?, 'STARTED', NOW())
            """;

    private static final String SELECT_BY_KEY_SQL = """
            SELECT idempotency_key, command_type, aggregate_id, request_hash, status, response_payload, created_at, completed_at
            FROM idempotency_records
            WHERE idempotency_key = ?
            """;

    private static final String COMPLETE_SQL = """
            UPDATE idempotency_records
            SET status = 'COMPLETED', response_payload = ?, completed_at = NOW()
            WHERE idempotency_key = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<IdempotencyRecord> rowMapper;

    public JdbcIdempotencyAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = (rs, rowNum) -> new IdempotencyRecord(
                rs.getString("idempotency_key"),
                rs.getString("command_type"),
                rs.getString("aggregate_id"),
                rs.getString("request_hash"),
                rs.getString("status"),
                rs.getString("response_payload")
        );
    }

    @Override
    public IdempotencyDecision start(String key, String commandType, String aggregateId, String requestHash) {
        try {
            jdbcTemplate.update(INSERT_STARTED_SQL, key, commandType, aggregateId, requestHash);
            return new IdempotencyDecision.Started(key);
        } catch (DuplicateKeyException e) {
            // Key already exists, load the existing record
            var records = jdbcTemplate.query(SELECT_BY_KEY_SQL, rowMapper, key);
            if (records.isEmpty()) {
                // Key disappeared (should not happen), treat as started
                return new IdempotencyDecision.Started(key);
            }
            IdempotencyRecord existing = records.getFirst();

            if ("COMPLETED".equals(existing.status()) && requestHash.equals(existing.requestHash())) {
                return new IdempotencyDecision.Replay(existing.responsePayload());
            }

            if (!requestHash.equals(existing.requestHash())) {
                return new IdempotencyDecision.Conflict("idempotency key reused with different request hash");
            }

            // STARTED but not completed
            return new IdempotencyDecision.Conflict("idempotency key already in progress");
        }
    }

    @Override
    public void complete(String key, String responsePayload) {
        int updated = jdbcTemplate.update(COMPLETE_SQL, responsePayload, key);
        if (updated != 1) {
            throw new IllegalStateException("Idempotency record not found for key: " + key);
        }
    }

    private record IdempotencyRecord(
            String idempotencyKey,
            String commandType,
            String aggregateId,
            String requestHash,
            String status,
            String responsePayload
    ) {}
}
