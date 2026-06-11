package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JdbcCreditAccountSummaryAdapter implements CreditAccountSummaryRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO credit_account_summary
              (credit_account_id, opened, credit_limit, outstanding_balance, authorized_amount, available_limit,
               authorizations, projected_version, last_event_id, updated_at)
            VALUES (?, ?, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?::jsonb, ?, ?, ?)
            ON CONFLICT (credit_account_id) DO UPDATE SET
              opened = EXCLUDED.opened,
              credit_limit = EXCLUDED.credit_limit,
              outstanding_balance = EXCLUDED.outstanding_balance,
              authorized_amount = EXCLUDED.authorized_amount,
              available_limit = EXCLUDED.available_limit,
              authorizations = EXCLUDED.authorizations,
              projected_version = EXCLUDED.projected_version,
              last_event_id = EXCLUDED.last_event_id,
              updated_at = EXCLUDED.updated_at
            """;

    private static final String FIND_BY_ID_SQL =
            "SELECT credit_account_id, opened, credit_limit, outstanding_balance, authorized_amount, available_limit, authorizations, projected_version, last_event_id, updated_at FROM credit_account_summary WHERE credit_account_id = ?";

    private static final String FIND_PAGE_SQL = """
            SELECT credit_account_id, opened, credit_limit, outstanding_balance, authorized_amount, available_limit, authorizations, projected_version, last_event_id, updated_at FROM credit_account_summary
            ORDER BY updated_at DESC, credit_account_id ASC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_SQL =
            "SELECT COUNT(*) FROM credit_account_summary";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void upsert(CreditAccountSummary summary) {
        jdbcTemplate.update(UPSERT_SQL,
                summary.creditAccountId(),
                summary.opened(),
                summary.creditLimit(),
                summary.outstandingBalance(),
                summary.authorizedAmount(),
                summary.availableLimit(),
                serializeAuthorizations(summary.authorizations()),
                summary.projectedVersion(),
                summary.lastEventId(),
                Timestamp.from(summary.updatedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreditAccountSummary> findById(CreditAccountId creditAccountId) {
        List<CreditAccountSummary> rows = jdbcTemplate.query(FIND_BY_ID_SQL, this::map, creditAccountId.value());
        return rows.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public CreditAccountSummaryPage findAll(CreditAccountSummaryPageRequest request) {
        List<CreditAccountSummary> items = jdbcTemplate.query(FIND_PAGE_SQL, this::map, request.size(), request.page() * request.size());
        Long total = jdbcTemplate.queryForObject(COUNT_SQL, Long.class);
        long totalItems = total == null ? 0L : total;
        int totalPages = request.size() == 0 ? 0 : (int) Math.ceil((double) totalItems / (double) request.size());
        return new CreditAccountSummaryPage(items, request.page(), request.size(), totalItems, totalPages);
    }

    private CreditAccountSummary map(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("credit_account_id", UUID.class);
        boolean opened = rs.getBoolean("opened");
        String creditLimit = rs.getString("credit_limit");
        String outstanding = rs.getString("outstanding_balance");
        String authorized = rs.getString("authorized_amount");
        String available = rs.getString("available_limit");
        String authsJson = rs.getString("authorizations");
        long projectedVersion = rs.getLong("projected_version");
        UUID lastEventId = rs.getObject("last_event_id", UUID.class);
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

        List<CreditAccountSummary.AuthorizationSummary> auths =
                deserializeAuthorizations(authsJson);

        return new CreditAccountSummary(id, opened, creditLimit, outstanding, authorized, available,
                auths, projectedVersion, lastEventId, updatedAt);
    }

    private String serializeAuthorizations(List<CreditAccountSummary.AuthorizationSummary> auths) {
        try {
            return objectMapper.writeValueAsString(auths);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize authorizations", e);
        }
    }

    private List<CreditAccountSummary.AuthorizationSummary> deserializeAuthorizations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CreditAccountSummary.AuthorizationSummary.class));
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to deserialize authorizations", e);
        }
    }
}
