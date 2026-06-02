package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanmoo.eventsourcing.creditaccount.application.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.application.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void appendThenLoadReturnsSameEventTypeAndData() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.newId();
        var occurredAt = Instant.now();
        var openedEvent = new CreditAccountOpened(creditAccountId, occurredAt);

        // when
        AppendResult result = eventStorePort.appendEvents(
                aggregateType, aggregateId, 0, List.of(openedEvent), Map.of());

        // then
        assertThat(result.newAggregateVersion()).isEqualTo(1);

        List<EventEnvelope> envelopes = eventStorePort.loadEvents(aggregateType, aggregateId);
        assertThat(envelopes).hasSize(1);

        EventEnvelope envelope = envelopes.getFirst();
        assertThat(envelope.event()).isInstanceOf(CreditAccountOpened.class);
        assertThat(envelope.aggregateVersion()).isEqualTo(1);
        assertThat(envelope.aggregateType()).isEqualTo(aggregateType);
        assertThat(envelope.aggregateId()).isEqualTo(aggregateId);

        CreditAccountOpened loaded = (CreditAccountOpened) envelope.event();
        assertThat(loaded.creditAccountId()).isEqualTo(creditAccountId);
        assertThat(loaded.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void duplicateAggregateVersionThrowsConcurrencyConflict() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.newId();
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
}
