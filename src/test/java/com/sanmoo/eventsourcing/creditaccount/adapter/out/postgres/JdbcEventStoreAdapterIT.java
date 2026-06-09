package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sanmoo.eventsourcing.creditaccount.PostgresTestImage;

@Testcontainers
@SpringBootTest
class JdbcEventStoreAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgresTestImage.POSTGRES_18);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EventStorePort eventStorePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTestState() {
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
        AppendResult result = eventStorePort.appendEvents(
                aggregateType, aggregateId, 0, List.of(openedEvent), metadata);

        // then
        assertThat(result.newAggregateVersion()).isEqualTo(1);

        List<EventEnvelope> envelopes = eventStorePort.loadEvents(aggregateType, aggregateId);
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
    void emptyMetadataIsPersistedAsEmptyJsonObject() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());

        // when
        eventStorePort.appendEvents(
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
        assertThat(eventStorePort.loadEvents(aggregateType, aggregateId).getFirst().metadata()).isEmpty();
    }

    @Test
    void sameAggregateIdAndVersionAreAllowedForDifferentAggregateTypes() {
        // given
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());

        // when
        eventStorePort.appendEvents(
                "CreditAccount", aggregateId, 0,
                List.of(new CreditAccountOpened(creditAccountId, Instant.now())),
                Map.of());
        AppendResult result = eventStorePort.appendEvents(
                "AnotherAggregate", aggregateId, 0,
                List.of(new CreditAccountOpened(creditAccountId, Instant.now())),
                Map.of());

        // then
        assertThat(result.newAggregateVersion()).isEqualTo(1);
        assertThat(eventStorePort.loadEvents("CreditAccount", aggregateId)).hasSize(1);
        assertThat(eventStorePort.loadEvents("AnotherAggregate", aggregateId)).hasSize(1);
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
        eventStorePort.appendEvents(
                aggregateType, aggregateId, 0, List.of(openedEvent), Map.of());

        // then - second append with same version throws
        assertThatThrownBy(() ->
                eventStorePort.appendEvents(
                        aggregateType, aggregateId, 0,
                        List.of(new CreditAccountOpened(creditAccountId, Instant.now())),
                        Map.of())
        )
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void appendPersistsVersionedEventTypeName() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());

        // when
        eventStorePort.appendEvents(
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
