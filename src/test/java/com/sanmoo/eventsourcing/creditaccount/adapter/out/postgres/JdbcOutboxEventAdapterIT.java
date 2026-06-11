package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStore;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(scripts = "/test/outbox-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class JdbcOutboxEventAdapterIT {

    @Autowired
    private JdbcOutboxEventAdapter outboxEventAdapter;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findPending_returnsUnprocessedEvents() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());
        var event1 = new CreditAccountOpened(creditAccountId, Instant.now());
        var event2 = new CreditAccountOpened(CreditAccountId.of(UUID.randomUUID()), Instant.now());

        // when
        eventStore.appendEvents(aggregateType, aggregateId, 0, List.of(event1, event2), Map.of());

        // then
        List<OutboxEvent> pending = outboxEventAdapter.findPending(10);
        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(e -> e.event() instanceof CreditAccountOpened);
    }

    @Test
    void markProcessed_removesEventFromPending() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());
        var event = new CreditAccountOpened(creditAccountId, Instant.now());

        eventStore.appendEvents(aggregateType, aggregateId, 0, List.of(event), Map.of());

        List<OutboxEvent> pending = outboxEventAdapter.findPending(10);
        assertThat(pending).hasSize(1);

        // when
        outboxEventAdapter.markProcessed(pending.getFirst().eventId());

        // then
        List<OutboxEvent> afterProcessed = outboxEventAdapter.findPending(10);
        assertThat(afterProcessed).isEmpty();
    }

    @Test
    void markFailed_incrementsAttemptsAndRecordsError() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());
        var event = new CreditAccountOpened(creditAccountId, Instant.now());

        eventStore.appendEvents(aggregateType, aggregateId, 0, List.of(event), Map.of());

        List<OutboxEvent> pending = outboxEventAdapter.findPending(10);
        assertThat(pending).hasSize(1);
        UUID eventId = pending.getFirst().eventId();

        // when
        outboxEventAdapter.markFailed(eventId, "boom");

        // then
        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT processing_attempts FROM outbox_events WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(attempts).isEqualTo(1);

        String lastError = jdbcTemplate.queryForObject(
                "SELECT last_error FROM outbox_events WHERE event_id = ?",
                String.class, eventId);
        assertThat(lastError).isEqualTo("boom");
    }
}
