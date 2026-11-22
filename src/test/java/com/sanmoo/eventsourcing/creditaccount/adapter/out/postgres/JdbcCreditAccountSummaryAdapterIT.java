package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.core.projection.CreditAccountSummaryProjector;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountBlocked;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(scripts = "/test/summary-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class JdbcCreditAccountSummaryAdapterIT {

    @Autowired
    JdbcCreditAccountSummaryAdapter adapter;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void upsertAndFindById_roundtrips() {
        UUID id = UUID.randomUUID();
        CreditAccountSummary summary = new CreditAccountSummary(
                id, true, "BLOCKED", "1000.00", "0.00", "0.00", "1000.00",
                List.of(), 1L, UUID.randomUUID(), Instant.now());

        adapter.upsert(summary);

        Optional<CreditAccountSummary> found = adapter.findById(CreditAccountId.of(id));
        assertThat(found).isPresent();
        assertThat(found.get().creditLimit()).isEqualTo("1000.00");
        assertThat(found.get().status()).isEqualTo("BLOCKED");
    }

    @Test
    void projectedBlockEventPersistsBlockedStatus() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        CreditAccountSummaryProjector projector = new CreditAccountSummaryProjector();
        var opened = new CreditAccountOpened(CreditAccountId.of(id), now);
        var openedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", id.toString(), 1L,
                "CreditAccountOpened", opened, java.util.Map.of(), now);
        CreditAccountSummary summary = projector.apply(openedEvent, projector.emptySummary(openedEvent));
        var blocked = new CreditAccountBlocked(CreditAccountId.of(id), "review", now);
        var blockedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", id.toString(), 2L,
                "CreditAccountBlocked", blocked, java.util.Map.of(), now);

        adapter.upsert(projector.apply(blockedEvent, summary));

        assertThat(adapter.findById(CreditAccountId.of(id))).isPresent()
                .get().extracting(CreditAccountSummary::status).isEqualTo("BLOCKED");
    }

    @Test
    void findAll_filtersStatusBeforeApplyingPaginationAndCountsFilteredRows() {
        UUID active1 = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();
        UUID active2 = UUID.randomUUID();
        Instant now = Instant.now();
        adapter.upsert(summary(active1, "ACTIVE", now.minusSeconds(30)));
        adapter.upsert(summary(blocked, "BLOCKED", now.minusSeconds(20)));
        adapter.upsert(summary(active2, "ACTIVE", now.minusSeconds(10)));

        CreditAccountSummaryPage page = adapter.findAll(new CreditAccountSummaryPageRequest(1, 1, "ACTIVE"));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().creditAccountId()).isEqualTo(active1);
        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    private CreditAccountSummary summary(UUID id, String status, Instant updatedAt) {
        return new CreditAccountSummary(id, true, status, "500.00", "0.00", "0.00", "500.00",
                List.of(), 1L, UUID.randomUUID(), updatedAt);
    }

    @Test
    void findAll_returnsPaginatedResultsOrderedByUpdatedAtDesc() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        adapter.upsert(new CreditAccountSummary(id1, true, "ACTIVE", "500.00", "0.00", "0.00", "500.00", List.of(), 1L, UUID.randomUUID(), Instant.now().minusSeconds(60)));
        adapter.upsert(new CreditAccountSummary(id2, true, "ACTIVE", "1000.00", "0.00", "0.00", "1000.00", List.of(), 1L, UUID.randomUUID(), Instant.now()));

        CreditAccountSummaryPage page = adapter.findAll(new CreditAccountSummaryPageRequest(0, 10));

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).creditAccountId()).isEqualTo(id2);
        assertThat(page.totalItems()).isEqualTo(2);
    }
}
