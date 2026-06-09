package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcIdempotencyAdapter implements IdempotencyPort {

    private static final String SET_LOCK_TIMEOUT_SQL = "SET LOCAL lock_timeout = '5s'";

    private static final String ADVISORY_LOCK_SQL = "SELECT pg_advisory_xact_lock(?)";

    private static final String SELECT_BY_KEY_SQL = """
            SELECT idempotency_key, command_type, aggregate_id, request_hash, response_payload, aggregate_version
            FROM idempotency_records
            WHERE idempotency_key = ?
            """;

    private static final String INSERT_RESULT_SQL = """
            INSERT INTO idempotency_records
                (idempotency_key, command_type, aggregate_id, request_hash, response_payload, aggregate_version, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
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
    public void lockKey(String key) {
        long lockId = lockId(key);
        try {
            jdbcTemplate.execute(SET_LOCK_TIMEOUT_SQL);
            jdbcTemplate.query(ADVISORY_LOCK_SQL, rs -> null, lockId);
        } catch (CannotAcquireLockException e) {
            throw new IdempotencyConflictException("idempotency key is currently being processed");
        }
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        return jdbcTemplate.query(SELECT_BY_KEY_SQL, rowMapper, key).stream().findFirst();
    }

    @Override
    public void saveResult(String key, String commandType, String aggregateId, String requestHash, String responsePayload, long aggregateVersion) {
        int inserted = jdbcTemplate.update(
                INSERT_RESULT_SQL,
                key,
                commandType,
                aggregateId,
                requestHash,
                responsePayload,
                aggregateVersion
        );
        if (inserted != 1) {
            throw new IllegalStateException("Idempotency result was not inserted for key: " + key);
        }
    }

    private long lockId(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("credit-account-idempotency:" + key).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
