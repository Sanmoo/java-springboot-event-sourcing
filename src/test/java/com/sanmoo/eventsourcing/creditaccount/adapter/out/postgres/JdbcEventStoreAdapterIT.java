package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AccountEvent;
import com.sanmoo.eventsourcing.creditaccount.core.port.AccountEventPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStore;
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCapturedV1;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCapturedV2;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PaymentReceived;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sanmoo.eventsourcing.creditaccount.PostgresTestImage;

@Testcontainers
@SpringBootTest
class JdbcEventStoreAdapterIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(PostgresTestImage.POSTGRES_18);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EventStore eventStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTestState() {
        jdbcTemplate.update("DELETE FROM outbox_deliveries");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM event_store");
        RecordingUniqueIdGenerator.clear();
    }

    @Test
    void appendThenLoadReturnsSameEventTypeAndData() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());
        var occurredAt = Instant.now();
        var openedEvent = new CreditAccountOpened(creditAccountId, occurredAt);

        // when
        Map<String, String> metadata = Map.of(
                "idempotencyKey", "metadata-key-1",
                "commandType", "OpenCreditAccount",
                "requestHash", "hash-1"
        );
        AppendResult result = eventStore.appendEvents(
                aggregateType, aggregateId, 0, List.of(openedEvent), metadata);

        // then
        assertThat(result.newAggregateVersion()).isEqualTo(1);

        List<EventEnvelope> envelopes = eventStore.loadEvents(aggregateType, aggregateId);
        assertThat(envelopes).hasSize(1);
        List<UUID> generatedIds = RecordingUniqueIdGenerator.generatedIds();
        assertThat(generatedIds).hasSize(1);

        EventEnvelope envelope = envelopes.getFirst();
        assertThat(envelope.metadata())
                .containsEntry("idempotencyKey", "metadata-key-1")
                .containsEntry("commandType", "OpenCreditAccount")
                .containsEntry("requestHash", "hash-1");
        assertThat(envelope.eventId()).isEqualTo(generatedIds.getFirst());
        assertThat(envelope.eventId().version()).isEqualTo(7);
        assertThat(envelope.event()).isInstanceOf(CreditAccountOpened.class);
        assertThat(envelope.aggregateVersion()).isEqualTo(1);
        assertThat(envelope.aggregateType()).isEqualTo(aggregateType);
        assertThat(envelope.aggregateId()).isEqualTo(aggregateId);

        CreditAccountOpened loaded = (CreditAccountOpened) envelope.event();
        assertThat(loaded.creditAccountId()).isEqualTo(creditAccountId);
        assertThat(loaded.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void appendThenLoadKeepsCapturedEventVersionAndAmount() {
        var accountId = CreditAccountId.of(UUID.randomUUID());
        var authorizationId = AuthorizationId.of(UUID.randomUUID());
        var occurredAt = Instant.parse("2026-06-01T10:03:00Z");
        var fullLegacy = new PurchaseCapturedV1(accountId, authorizationId, occurredAt);
        var partial = new PurchaseCapturedV2(accountId, authorizationId, Money.of("12.50"), occurredAt);
        String legacyAggregate = UUID.randomUUID().toString();
        String partialAggregate = UUID.randomUUID().toString();
        eventStore.appendEvents("CreditAccount", legacyAggregate, 0, List.of(fullLegacy), Map.of());
        eventStore.appendEvents("CreditAccount", partialAggregate, 0, List.of(partial), Map.of());
        jdbcTemplate.update("UPDATE event_store SET payload = ?::jsonb WHERE aggregate_id = ?", "{\"creditAccountId\":{\"value\":\"" + accountId.value() + "\"},\"authorizationId\":{\"value\":\"" + authorizationId.value() + "\"},\"occurredAt\":\"" + occurredAt + "\"}", legacyAggregate);
        jdbcTemplate.update("UPDATE outbox_events SET payload = ?::jsonb WHERE aggregate_id = ?", "{\"creditAccountId\":{\"value\":\"" + accountId.value() + "\"},\"authorizationId\":{\"value\":\"" + authorizationId.value() + "\"},\"occurredAt\":\"" + occurredAt + "\"}", legacyAggregate);

        var legacyEnvelope = eventStore.loadEvents("CreditAccount", legacyAggregate).getFirst();
        var partialEnvelope = eventStore.loadEvents("CreditAccount", partialAggregate).getFirst();
        assertThat(legacyEnvelope.event()).isEqualTo(fullLegacy);
        assertThat(partialEnvelope.event()).isEqualTo(partial);
        assertThat(jdbcTemplate.queryForObject("SELECT event_type FROM event_store WHERE aggregate_id = ?", String.class, legacyAggregate))
                .isEqualTo("purchase.captured.v1");
        assertThat(jdbcTemplate.queryForObject("SELECT event_type FROM event_store WHERE aggregate_id = ?", String.class, partialAggregate))
                .isEqualTo("purchase.captured.v2");
    }

    @TestConfiguration
    static class DeterministicIdGeneratorConfiguration {

        @Bean
        @Primary
        UniqueIdGenerator uniqueIdGenerator() {
            return RecordingUniqueIdGenerator::generate;
        }
    }

    static final class RecordingUniqueIdGenerator {

        private static final List<UUID> GENERATED_IDS = new CopyOnWriteArrayList<>();
        private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

        private RecordingUniqueIdGenerator() {
        }

        static void clear() {
            GENERATED_IDS.clear();
            SEQUENCE.set(1);
        }

        static List<UUID> generatedIds() {
            return List.copyOf(GENERATED_IDS);
        }

        static UUID generate() {
            UUID id = UUID.fromString("018f5f4b-6a3c-7000-8000-%012d".formatted(SEQUENCE.getAndIncrement()));
            GENERATED_IDS.add(id);
            return id;
        }
    }

    @Test
    void accountHistoryIsOrderedAndPagedByAggregateVersion() {
        UUID id = UUID.randomUUID();
        var accountId = CreditAccountId.of(id);
        var openedAt = Instant.parse("2025-01-01T00:00:00Z");
        var limitAt = Instant.parse("2025-01-01T00:00:01Z");
        eventStore.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(accountId, openedAt)), Map.of());
        eventStore.appendEvents("CreditAccount", id.toString(), 1,
                List.of(new CreditLimitAssigned(accountId, Money.of("100.00"), limitAt)), Map.of());

        AccountEventPage first = eventStore.readAccountEvents(id, 1, 1);
        AccountEventPage second = eventStore.readAccountEvents(id, 2, 1);
        AccountEventPage unknown = eventStore.readAccountEvents(UUID.randomUUID(), 1, 50);
        assertThat(first.events()).hasSize(1);
        assertThat(first.events().getFirst().version()).isEqualTo(1);
        assertThat(first.events().getFirst().type()).isEqualTo("credit-account.opened.v1");
        assertThat(first.events().getFirst().occurredAt()).isEqualTo(openedAt);
        assertThat(first.events().getFirst().payload()).containsKey("creditAccountId");
        assertThat(first.nextFromVersion()).isEqualTo(2L);
        assertThat(second.events()).hasSize(1);
        assertThat(second.events().getFirst().version()).isEqualTo(2);
        assertThat(second.nextFromVersion()).isNull();
        assertThat(unknown.events()).isEmpty();
        assertThat(unknown.nextFromVersion()).isNull();
        assertThat(unknown.accountExists()).isFalse();
        assertThat(first.accountExists()).isTrue();
    }

    @Test
    void accountHistoryPagingContinuesAtTheNextStoredVersionAcrossVersionGaps() {
        UUID id = UUID.randomUUID();
        var accountId = CreditAccountId.of(id);
        eventStore.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(accountId, Instant.parse("2025-01-01T00:00:00Z"))), Map.of());
        eventStore.appendEvents("CreditAccount", id.toString(), 1,
                List.of(new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2025-01-01T00:00:01Z"))), Map.of());
        eventStore.appendEvents("CreditAccount", id.toString(), 2,
                List.of(new PaymentReceived(
                        accountId, Money.of("1.00"), Instant.parse("2025-01-01T00:00:02Z"))), Map.of());
        jdbcTemplate.update("UPDATE event_store SET aggregate_version = 4 WHERE aggregate_type = 'CreditAccount' AND aggregate_id = ? AND aggregate_version = 3", id.toString());

        AccountEventPage first = eventStore.readAccountEvents(id, 1, 2);

        assertThat(first.events()).extracting(AccountEvent::version).containsExactly(1L, 2L);
        assertThat(first.nextFromVersion()).isEqualTo(4L);
        AccountEventPage second = eventStore.readAccountEvents(id, first.nextFromVersion(), 2);
        assertThat(second.events()).extracting(AccountEvent::version).containsExactly(4L);
        assertThat(second.nextFromVersion()).isNull();
    }

    @Test
    void emptyMetadataIsPersistedAsEmptyJsonObject() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());

        // when
        eventStore.appendEvents(
                aggregateType, aggregateId, 0,
                List.of(new CreditAccountOpened(creditAccountId, Instant.now())),
                Map.of());

        // then
        String metadata = jdbcTemplate.queryForObject(
                "SELECT metadata::text FROM event_store WHERE aggregate_type = ? AND aggregate_id = ?",
                String.class,
                aggregateType,
                aggregateId);
        assertThat(metadata).isEqualTo("{}");
        assertThat(eventStore.loadEvents(aggregateType, aggregateId).getFirst().metadata()).isEmpty();
    }

    @Test
    void sameAggregateIdAndVersionAreAllowedForDifferentAggregateTypes() {
        // given
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());

        // when
        eventStore.appendEvents(
                "CreditAccount", aggregateId, 0,
                List.of(new CreditAccountOpened(creditAccountId, Instant.now())),
                Map.of());
        AppendResult result = eventStore.appendEvents(
                "AnotherAggregate", aggregateId, 0,
                List.of(new CreditAccountOpened(creditAccountId, Instant.now())),
                Map.of());

        // then
        assertThat(result.newAggregateVersion()).isEqualTo(1);
        assertThat(eventStore.loadEvents("CreditAccount", aggregateId)).hasSize(1);
        assertThat(eventStore.loadEvents("AnotherAggregate", aggregateId)).hasSize(1);
    }

    @Test
    void duplicateAggregateVersionThrowsConcurrencyConflict() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());
        var occurredAt = Instant.now();
        var openedEvent = new CreditAccountOpened(creditAccountId, occurredAt);

        // when - first append succeeds
        eventStore.appendEvents(
                aggregateType, aggregateId, 0, List.of(openedEvent), Map.of());

        // then - second append with same version throws
        assertThatThrownBy(() ->
                eventStore.appendEvents(
                        aggregateType, aggregateId, 0,
                        List.of(new CreditAccountOpened(creditAccountId, Instant.now())),
                        Map.of())
        )
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void appendEventsPersistsEventsAndOutboxRows() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        CreditAccountOpened opened = new CreditAccountOpened(creditAccountId, now);

        // when
        AppendResult result = eventStore.appendEvents(
                aggregateType, aggregateId, 0, List.of(opened), Map.of());

        // then
        assertThat(result.newAggregateVersion()).isEqualTo(1);

        Integer eventStoreCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_store WHERE aggregate_id = ?",
                Integer.class, aggregateId);
        assertThat(eventStoreCount).isEqualTo(1);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class, aggregateId);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void appendPersistsVersionedEventTypeName() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());

        // when
        eventStore.appendEvents(
                aggregateType, aggregateId, 0,
                List.of(new CreditAccountOpened(creditAccountId, Instant.now())),
                Map.of());

        // then
        String eventType = jdbcTemplate.queryForObject(
                "SELECT event_type FROM event_store WHERE aggregate_type = ? AND aggregate_id = ?",
                String.class,
                aggregateType,
                aggregateId);
        assertThat(eventType).isEqualTo("credit-account.opened.v1");
    }
}
