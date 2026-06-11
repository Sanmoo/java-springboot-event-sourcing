package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
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

    @Test
    void upsertAndFindById_roundtrips() {
        UUID id = UUID.randomUUID();
        CreditAccountSummary summary = new CreditAccountSummary(
                id, true, "1000.00", "0.00", "0.00", "1000.00",
                List.of(), 1L, UUID.randomUUID(), Instant.now());

        adapter.upsert(summary);

        Optional<CreditAccountSummary> found = adapter.findById(CreditAccountId.of(id));
        assertThat(found).isPresent();
        assertThat(found.get().creditLimit()).isEqualTo("1000.00");
    }

    @Test
    void findAll_returnsPaginatedResultsOrderedByUpdatedAtDesc() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        adapter.upsert(new CreditAccountSummary(id1, true, "500.00", "0.00", "0.00", "500.00", List.of(), 1L, UUID.randomUUID(), Instant.now().minusSeconds(60)));
        adapter.upsert(new CreditAccountSummary(id2, true, "1000.00", "0.00", "0.00", "1000.00", List.of(), 1L, UUID.randomUUID(), Instant.now()));

        CreditAccountSummaryPage page = adapter.findAll(new CreditAccountSummaryPageRequest(0, 10));

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).creditAccountId()).isEqualTo(id2);
        assertThat(page.totalItems()).isEqualTo(2);
    }
}
