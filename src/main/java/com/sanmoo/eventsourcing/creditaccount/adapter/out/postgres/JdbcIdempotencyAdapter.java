package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JdbcIdempotencyAdapter implements IdempotencyPort {

    private static final String LOCK_SQL = """
            INSERT INTO idempotency_records (idempotency_key, command_type, aggregate_id, request_hash, status, created_at)
            VALUES (?, ?, ?, ?, 'STARTED', NOW())
            """;

    private static final String SELECT_BY_KEY_SQL = """
            SELECT idempotency_key, command_type, aggregate_id, request_hash, status, response_payload, aggregate_version, created_at, completed_at
            FROM idempotency_records
            WHERE idempotency_key = ?
            """;

    private static final String SAVE_RESULT_SQL = """
            UPDATE idempotency_records
            SET status = 'COMPLETED', response_payload = ?, aggregate_version = ?, completed_at = NOW()
            WHERE idempotency_key = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<IdempotencyRecord> rowMapper = (rs, rowNum) -> new IdempotencyRecord(
            rs.getString("idempotency_key"),
            rs.getString("command_type"),
            rs.getString("aggregate_id"),
            rs.getString("request_hash"),
            rs.getString("response_payload"),
            rs.getLong("aggregate_version")
    );

    @Override
    @Transactional
    public void lockKey(String key) {
        try {
            jdbcTemplate.update(LOCK_SQL, key, "", "", "");
        } catch (DuplicateKeyException e) {
            // Key already exists; lock is already held or was acquired earlier.
            // Within the same transaction, this is fine.
        }
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        var records = jdbcTemplate.query(SELECT_BY_KEY_SQL, rowMapper, key);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyRecord record = records.getFirst();
        if (record.responsePayload() == null) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    @Override
    public void saveResult(String key, String commandType, String aggregateId, String requestHash, String responsePayload, long aggregateVersion) {
        int updated = jdbcTemplate.update(SAVE_RESULT_SQL, responsePayload, aggregateVersion, key);
        if (updated != 1) {
            throw new IllegalStateException("Idempotency record not found for key: " + key);
        }
    }
}
