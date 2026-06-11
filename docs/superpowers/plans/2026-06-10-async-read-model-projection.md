# Async Read Model Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace aggregate rehydration on `GET /credit-accounts/{id}` with an asynchronous `credit_account_summary` projection fed by an outbox, and add a paginated `GET /credit-accounts` list endpoint.

**Architecture:** Commands continue to persist events to `event_store`; the same transaction also writes matching rows to a new `outbox_events` table. A scheduled worker reads pending outbox rows and applies them to a new `credit_account_summary` table. `GET` endpoints read only the summary. The `OutboxPort`/port types are renamed to drop generic `Port` suffixes.

**Tech Stack:** Java 25, Spring Boot 4.0.6, PostgreSQL 18, Liquibase, JdbcTemplate, Spring `@Scheduled`, JUnit 5, AssertJ, Testcontainers.

---

## File Structure

### New files
- `src/main/resources/db/changelog/004-create-outbox-events.yaml` — outbox table.
- `src/main/resources/db/changelog/005-create-credit-account-summary.yaml` — read model table.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/OutboxEventRepository.java` — outbox read/control port.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/CreditAccountSummaryRepository.java` — summary read/upsert port.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/OutboxEvent.java` — outbox record.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/CreditAccountSummary.java` — read model record.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/CreditAccountSummaryPageRequest.java` — paging request.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/CreditAccountSummaryPage.java` — paging result.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/InvalidPageSizeException.java` — page size validation error.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjector.java` — applies events to summary.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionTick.java` — processed row record.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java` — fetches outbox rows and projects.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCase.java` — list use case.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ListCreditAccountsInput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ListCreditAccountsOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/CreditAccountSummaryOutput.java` — single summary in list output (without re-embedding authorizations list repeated; uses same shape as `CreditAccountOutput` plus `projectedVersion`).
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/ProjectionConflictException.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/SummaryNotFoundException.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/ProjectionNotReadyException.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcOutboxEventAdapter.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcCreditAccountSummaryAdapter.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/scheduler/OutboxProjectionWorkerRunner.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/CreditAccountSummaryResponse.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/PageResponse.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/ProjectionNotReadyResponse.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/projection/ProjectionProperties.java` — `@ConfigurationProperties`.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcOutboxEventAdapterIT.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcCreditAccountSummaryAdapterIT.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCaseTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerListIT.java`

### Modified files
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/EventStorePort.java` — rename to `EventStore.java`.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyPort.java` — rename to `IdempotencyRepository.java`.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/AppendResult.java` — keep, but update `EventStore` callers.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java` — implement `EventStore`, write to `outbox_events` in the same transaction.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java` — implement `IdempotencyRepository`.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java` — use renamed ports; expose `aggregateVersion` in output.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java` — read from `CreditAccountSummaryRepository`.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/CreditAccountOutput.java` — add `projectedVersion` (nullable Long) and keep `aggregateVersion` semantics.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/GetCreditAccountInput.java` — add `Long minVersion`.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java` — add list endpoint and `minVersion` query param handling.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestExceptionHandler.java` — handle new errors.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/AssignCreditLimitRequest.java` — no change.
- `src/main/resources/application.yml` — add `credit-account.projections` properties.
- `src/main/resources/db/changelog/db.changelog-master.yaml` — include new changelogs.
- `README.md` — update GET behavior and new endpoint.
- All existing tests using the old port names will be updated.

### Removed
- `EventStorePort`, `IdempotencyPort` (renamed to `EventStore`, `IdempotencyRepository`).

---

## Task 1: Rename existing ports to drop `Port` suffix

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/EventStorePort.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyPort.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`
- Modify: tests in `src/test/...` and `src/qualityTest/...` referencing old names.

- [ ] **Step 1: Rename interface files and content**

In `core/port/EventStorePort.java`, rename file to `EventStore.java`. Replace the type declaration:

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import java.util.List;
import java.util.Map;

public interface EventStore {
    List<EventEnvelope> loadEvents(String aggregateType, String aggregateId);
    AppendResult appendEvents(String aggregateType, String aggregateId, long expectedVersion, List<CreditAccountEvent> events, Map<String, String> metadata);
}
```

In `core/port/IdempotencyPort.java`, rename file to `IdempotencyRepository.java`. Replace the type declaration:

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.Optional;

public interface IdempotencyRepository {
    void lockKey(String idempotencyKey);
    Optional<IdempotencyRecord> findByKey(String idempotencyKey);
    void saveResult(String idempotencyKey, String commandType, String aggregateId, String requestHash, String responsePayload, long aggregateVersion);
}
```

- [ ] **Step 2: Update adapters' implements clause**

In `JdbcEventStoreAdapter.java`:

```java
public class JdbcEventStoreAdapter implements EventStore {
```

In `JdbcIdempotencyAdapter.java`:

```java
public class JdbcIdempotencyAdapter implements IdempotencyRepository {
```

Update imports accordingly. Remove `implements EventStorePort` / `implements IdempotencyPort` text.

- [ ] **Step 3: Update `CreditAccountUseCaseSupport.java` field types**

Change:

```java
private final EventStorePort eventStore;
private final IdempotencyPort idempotencyPort;
```

to:

```java
private final EventStore eventStore;
private final IdempotencyRepository idempotencyPort;
```

Update imports.

- [ ] **Step 4: Update all tests and quality tests**

Run:

```bash
grep -rln "EventStorePort\|IdempotencyPort" src
```

For every match, replace occurrences with `EventStore` and `IdempotencyRepository` respectively. Update imports.

- [ ] **Step 5: Verify the build**

Run: `./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: rename port interfaces to drop Port suffix"
```

---

## Task 2: Add outbox table migration

**Files:**
- Create: `src/main/resources/db/changelog/004-create-outbox-events.yaml`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Write migration `004-create-outbox-events.yaml`**

```yaml
databaseChangeLog:
  - changeSet:
      id: 004-create-outbox-events
      author: sanmoo
      changes:
        - createTable:
            tableName: outbox_events
            columns:
              - column:
                  name: event_id
                  type: UUID
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: aggregate_type
                  type: VARCHAR(100)
                  constraints:
                    nullable: false
              - column:
                  name: aggregate_id
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: aggregate_version
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: event_type
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: payload
                  type: JSONB
                  constraints:
                    nullable: false
              - column:
                  name: metadata
                  type: JSONB
                  defaultValueComputed: "'{}'::jsonb"
                  constraints:
                    nullable: false
              - column:
                  name: occurred_at
                  type: TIMESTAMP WITH TIME ZONE
                  constraints:
                    nullable: false
              - column:
                  name: processed_at
                  type: TIMESTAMP WITH TIME ZONE
              - column:
                  name: processing_attempts
                  type: INT
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
              - column:
                  name: last_error
                  type: TEXT
        - addUniqueConstraint:
            tableName: outbox_events
            columnNames: aggregate_type, aggregate_id, aggregate_version
            constraintName: uq_outbox_events_aggregate_type_id_version
        - createIndex:
            tableName: outbox_events
            indexName: idx_outbox_events_pending
            columns:
              - column:
                  name: occurred_at
```

- [ ] **Step 2: Include the new migration in the master changelog**

In `db.changelog-master.yaml`, append:

```yaml
  - include:
      file: 004-create-outbox-events.yaml
```

- [ ] **Step 3: Run tests to verify Liquibase applies migration**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. Testcontainers apply Liquibase to fresh Postgres; migration runs.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/changelog/004-create-outbox-events.yaml \
        src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat(db): add outbox_events table"
```

---

## Task 3: Add credit_account_summary table migration

**Files:**
- Create: `src/main/resources/db/changelog/005-create-credit-account-summary.yaml`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml`

- [ ] **Step 1: Write migration `005-create-credit-account-summary.yaml`**

```yaml
databaseChangeLog:
  - changeSet:
      id: 005-create-credit-account-summary
      author: sanmoo
      changes:
        - createTable:
            tableName: credit_account_summary
            columns:
              - column:
                  name: credit_account_id
                  type: UUID
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: opened
                  type: BOOLEAN
                  constraints:
                    nullable: false
              - column:
                  name: credit_limit
                  type: NUMERIC(19,2)
              - column:
                  name: outstanding_balance
                  type: NUMERIC(19,2)
                  constraints:
                    nullable: false
              - column:
                  name: authorized_amount
                  type: NUMERIC(19,2)
                  constraints:
                    nullable: false
              - column:
                  name: available_limit
                  type: NUMERIC(19,2)
                  constraints:
                    nullable: false
              - column:
                  name: authorizations
                  type: JSONB
                  constraints:
                    nullable: false
              - column:
                  name: projected_version
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: last_event_id
                  type: UUID
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: TIMESTAMP WITH TIME ZONE
                  constraints:
                    nullable: false
```

- [ ] **Step 2: Include the new migration in the master changelog**

Append to `db.changelog-master.yaml`:

```yaml
  - include:
      file: 005-create-credit-account-summary.yaml
```

- [ ] **Step 3: Run tests to verify migration applies**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/changelog/005-create-credit-account-summary.yaml \
        src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat(db): add credit_account_summary read model table"
```

---

## Task 4: Update event store adapter to write outbox in same transaction

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java`

- [ ] **Step 1: Add outbox insert SQL constant**

In `JdbcEventStoreAdapter`, add:

```java
private static final String INSERT_OUTBOX_SQL = """
        INSERT INTO outbox_events
          (event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, metadata, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
        """;
```

- [ ] **Step 2: Modify `appendEvents` to insert into both tables**

Replace the loop body inside `appendEvents` so it inserts an event row and a matching outbox row:

```java
@Override
@Transactional
public AppendResult appendEvents(String aggregateType, String aggregateId, long expectedVersion, List<CreditAccountEvent> events, Map<String, String> metadata) {
    try {
        long version = expectedVersion;
        for (CreditAccountEvent event : events) {
            version++;
            UUID eventId = uniqueIdGenerator.generate();
            String eventType = eventTypeMapper.eventType(event);
            String payload = eventTypeMapper.serialize(event);
            String metadataJson = serializeMetadata(metadata);
            Instant occurredAt = event.occurredAt();

            jdbcTemplate.update(INSERT_EVENT_SQL,
                    eventId, aggregateId, aggregateType, version, eventType, payload, metadataJson, Timestamp.from(occurredAt));

            jdbcTemplate.update(INSERT_OUTBOX_SQL,
                    eventId, aggregateType, aggregateId, version, eventType, payload, metadataJson, Timestamp.from(occurredAt));
        }
        return new AppendResult(version);
    } catch (DataIntegrityViolationException e) {
        throw new ConcurrencyConflictException(aggregateType, aggregateId, expectedVersion, e);
    }
}
```

- [ ] **Step 3: Add an integration test asserting outbox rows are created**

In `JdbcEventStoreAdapterIT`, add a new test method:

```java
@Test
void appendEvents_persistsEventsAndOutboxRows() {
    UUID eventId = UUID.randomUUID();
    when(uniqueIdGenerator.generate()).thenReturn(eventId);

    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    CreditAccountOpened opened = new CreditAccountOpened(now);
    AppendResult result = adapter.appendEvents(
            "CreditAccount", aggregateId.toString(), 0, List.of(opened), Map.of());

    assertThat(result.newAggregateVersion()).isEqualTo(1);

    Integer eventStoreCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_store WHERE aggregate_id = ?",
            Integer.class, aggregateId.toString());
    assertThat(eventStoreCount).isEqualTo(1);

    Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
            Integer.class, aggregateId.toString());
    assertThat(outboxCount).isEqualTo(1);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests *JdbcEventStoreAdapterIT`
Expected: BUILD SUCCESSFUL. The new test passes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java
git commit -m "feat(event-store): also write events to outbox_events"
```

---

## Task 5: Define `OutboxEvent` and `CreditAccountSummary` records

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/OutboxEvent.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/CreditAccountSummary.java`

- [ ] **Step 1: Create `OutboxEvent` record**

```java
package com.sanmoo.eventsourcing.creditaccount.core.port.model;

import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String eventType,
        CreditAccountEvent event,
        Map<String, String> metadata,
        Instant occurredAt
) {}
```

- [ ] **Step 2: Create `CreditAccountSummary` record**

```java
package com.sanmoo.eventsourcing.creditaccount.core.port.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreditAccountSummary(
        UUID creditAccountId,
        boolean opened,
        String creditLimit,
        String outstandingBalance,
        String authorizedAmount,
        String availableLimit,
        List<AuthorizationSummary> authorizations,
        long projectedVersion,
        UUID lastEventId,
        Instant updatedAt
) {
    public record AuthorizationSummary(
            UUID authorizationId,
            String amount,
            String status,
            String merchantName
    ) {}
}
```

- [ ] **Step 3: Compile to confirm types**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/OutboxEvent.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/CreditAccountSummary.java
git commit -m "feat(model): add OutboxEvent and CreditAccountSummary records"
```

---

## Task 6: Define paging DTOs

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/CreditAccountSummaryPageRequest.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/CreditAccountSummaryPage.java`

- [ ] **Step 1: Create `CreditAccountSummaryPageRequest` record**

```java
package com.sanmoo.eventsourcing.creditaccount.core.port.model;

public record CreditAccountSummaryPageRequest(int page, int size) {}
```

- [ ] **Step 2: Create `CreditAccountSummaryPage` record**

```java
package com.sanmoo.eventsourcing.creditaccount.core.port.model;

import java.util.List;

public record CreditAccountSummaryPage(
        List<CreditAccountSummary> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/CreditAccountSummaryPageRequest.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/model/CreditAccountSummaryPage.java
git commit -m "feat(model): add paging DTOs for summary queries"
```

---

## Task 7: Define `OutboxEventRepository` and `CreditAccountSummaryRepository` ports

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/OutboxEventRepository.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/CreditAccountSummaryRepository.java`

- [ ] **Step 1: Create `OutboxEventRepository`**

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {
    List<OutboxEvent> findPending(int limit);
    void markProcessed(UUID eventId);
    void markFailed(UUID eventId, String error);
}
```

- [ ] **Step 2: Create `CreditAccountSummaryRepository`**

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

import java.util.Optional;

public interface CreditAccountSummaryRepository {
    Optional<CreditAccountSummary> findById(CreditAccountId creditAccountId);
    void upsert(CreditAccountSummary summary);
    CreditAccountSummaryPage findAll(CreditAccountSummaryPageRequest request);
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/OutboxEventRepository.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/CreditAccountSummaryRepository.java
git commit -m "feat(ports): add outbox and summary repository interfaces"
```

---

## Task 8: Implement `JdbcOutboxEventAdapter`

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcOutboxEventAdapter.java`
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcOutboxEventAdapterIT.java`

- [ ] **Step 1: Write the failing integration test**

In `JdbcOutboxEventAdapterIT`:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.adapter.out.uuid.UuidV7Generator;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(scripts = "/test/outbox-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class JdbcOutboxEventAdapterIT {

    @Autowired
    JdbcOutboxEventAdapter adapter;

    @Autowired
    JdbcEventStoreAdapter eventStoreAdapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void findPending_returnsUnprocessedEvents() {
        UUID accountId = UUID.randomUUID();
        eventStoreAdapter.appendEvents(
                "CreditAccount", accountId.toString(), 0,
                List.of(new CreditAccountOpened(Instant.now())),
                java.util.Map.of());
        eventStoreAdapter.appendEvents(
                "CreditAccount", accountId.toString(), 1,
                List.of(new com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned(Instant.now(), "1000.00")),
                java.util.Map.of());

        List<OutboxEvent> pending = adapter.findPending(10);
        assertThat(pending).hasSize(2);
    }

    @Test
    void markProcessed_removesEventFromPending() {
        UUID accountId = UUID.randomUUID();
        eventStoreAdapter.appendEvents(
                "CreditAccount", accountId.toString(), 0,
                List.of(new CreditAccountOpened(Instant.now())),
                java.util.Map.of());

        OutboxEvent event = adapter.findPending(10).get(0);
        adapter.markProcessed(event.eventId());

        List<OutboxEvent> pending = adapter.findPending(10);
        assertThat(pending).isEmpty();
    }

    @Test
    void markFailed_incrementsAttemptsAndRecordsError() {
        UUID accountId = UUID.randomUUID();
        eventStoreAdapter.appendEvents(
                "CreditAccount", accountId.toString(), 0,
                List.of(new CreditAccountOpened(Instant.now())),
                java.util.Map.of());

        OutboxEvent event = adapter.findPending(10).get(0);
        adapter.markFailed(event.eventId(), "boom");

        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT processing_attempts FROM outbox_events WHERE event_id = ?",
                Integer.class, event.eventId());
        String lastError = jdbcTemplate.queryForObject(
                "SELECT last_error FROM outbox_events WHERE event_id = ?",
                String.class, event.eventId());

        assertThat(attempts).isEqualTo(1);
        assertThat(lastError).isEqualTo("boom");
    }
}
```

- [ ] **Step 2: Run test to verify it fails (class not found)**

Run: `./gradlew test --tests *JdbcOutboxEventAdapterIT`
Expected: FAIL (compilation error: cannot find symbol JdbcOutboxEventAdapter).

- [ ] **Step 3: Implement `JdbcOutboxEventAdapter`**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JdbcOutboxEventAdapter implements OutboxEventRepository {

    private static final String FIND_PENDING_SQL = """
            SELECT event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, metadata, occurred_at
            FROM outbox_events
            WHERE processed_at IS NULL
            ORDER BY occurred_at ASC, event_id ASC
            LIMIT ?
            """;

    private static final String MARK_PROCESSED_SQL =
            "UPDATE outbox_events SET processed_at = ? WHERE event_id = ?";

    private static final String MARK_FAILED_SQL =
            "UPDATE outbox_events SET processing_attempts = processing_attempts + 1, last_error = ? WHERE event_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final EventTypeMapper eventTypeMapper;

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return jdbcTemplate.query(FIND_PENDING_SQL, (rs, rowNum) -> map(rs), limit);
    }

    @Override
    @Transactional
    public void markProcessed(UUID eventId) {
        jdbcTemplate.update(MARK_PROCESSED_SQL, Timestamp.from(Instant.now()), eventId);
    }

    @Override
    @Transactional
    public void markFailed(UUID eventId, String error) {
        jdbcTemplate.update(MARK_FAILED_SQL, truncate(error), eventId);
    }

    private OutboxEvent map(ResultSet rs) throws SQLException {
        UUID eventId = rs.getObject("event_id", UUID.class);
        String aggregateType = rs.getString("aggregate_type");
        String aggregateId = rs.getString("aggregate_id");
        long aggregateVersion = rs.getLong("aggregate_version");
        String eventType = rs.getString("event_type");
        String payload = rs.getString("payload");
        String metadataJson = rs.getString("metadata");
        Timestamp occurredAt = rs.getTimestamp("occurred_at");

        var event = eventTypeMapper.deserialize(eventType, payload);
        Map<String, String> metadata = eventTypeMapper.deserializeMetadata(metadataJson);
        return new OutboxEvent(eventId, aggregateType, aggregateId, aggregateVersion, eventType, event, metadata, occurredAt.toInstant());
    }

    private String truncate(String error) {
        if (error == null) return null;
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }
}
```

- [ ] **Step 4: Create `src/test/resources/test/outbox-cleanup.sql`**

```sql
DELETE FROM outbox_events;
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests *JdbcOutboxEventAdapterIT`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcOutboxEventAdapter.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcOutboxEventAdapterIT.java \
        src/test/resources/test/outbox-cleanup.sql
git commit -m "feat(outbox): implement JdbcOutboxEventAdapter"
```

---

## Task 9: Implement `JdbcCreditAccountSummaryAdapter`

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcCreditAccountSummaryAdapter.java`
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcCreditAccountSummaryAdapterIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
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
    void upsert_andFindById_roundtrips() {
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests *JdbcCreditAccountSummaryAdapterIT`
Expected: FAIL (compilation error: cannot find symbol JdbcCreditAccountSummaryAdapter).

- [ ] **Step 3: Implement `JdbcCreditAccountSummaryAdapter`**

```java
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
            "SELECT * FROM credit_account_summary WHERE credit_account_id = ?";

    private static final String FIND_PAGE_SQL = """
            SELECT * FROM credit_account_summary
            ORDER BY updated_at DESC, credit_account_id ASC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_SQL =
            "SELECT COUNT(*) FROM credit_account_summary";

    private final JdbcTemplate jdbcTemplate;
    private final EventTypeMapper eventTypeMapper; // reused for authorizations JSON

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
                eventTypeMapper.serializeAuthorizations(summary.authorizations()),
                summary.projectedVersion(),
                summary.lastEventId(),
                Timestamp.from(summary.updatedAt()));
    }

    @Override
    public Optional<CreditAccountSummary> findById(CreditAccountId creditAccountId) {
        List<CreditAccountSummary> rows = jdbcTemplate.query(FIND_BY_ID_SQL, this::map, creditAccountId.value());
        return rows.stream().findFirst();
    }

    @Override
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
                eventTypeMapper.deserializeAuthorizations(authsJson);

        return new CreditAccountSummary(id, opened, creditLimit, outstanding, authorized, available, auths, projectedVersion, lastEventId, updatedAt);
    }
}
```

- [ ] **Step 4: Add authorizations helpers to `EventTypeMapper`**

Modify `EventTypeMapper` (use the existing Jackson `ObjectMapper`) by adding two methods:

```java
public String serializeAuthorizations(List<CreditAccountSummary.AuthorizationSummary> auths) {
    try {
        return objectMapper.writeValueAsString(auths);
    } catch (JacksonException e) {
        throw new RuntimeException("Failed to serialize authorizations", e);
    }
}

public List<CreditAccountSummary.AuthorizationSummary> deserializeAuthorizations(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
        return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, CreditAccountSummary.AuthorizationSummary.class));
    } catch (JacksonException e) {
        throw new RuntimeException("Failed to deserialize authorizations", e);
    }
}
```

- [ ] **Step 5: Create cleanup script `src/test/resources/test/summary-cleanup.sql`**

```sql
DELETE FROM credit_account_summary;
```

- [ ] **Step 6: Run the integration test to verify it passes**

Run: `./gradlew test --tests *JdbcCreditAccountSummaryAdapterIT`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcCreditAccountSummaryAdapter.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcCreditAccountSummaryAdapterIT.java \
        src/test/resources/test/summary-cleanup.sql \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/EventTypeMapper.java
git commit -m "feat(summary): implement JdbcCreditAccountSummaryAdapter"
```

---

## Task 10: Implement `CreditAccountSummaryProjector`

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjector.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionTick.java`
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java`

- [ ] **Step 1: Write the failing unit test**

In `CreditAccountSummaryProjectorTest`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreditAccountSummaryProjectorTest {

    private final CreditAccountSummaryProjector projector = new CreditAccountSummaryProjector();

    @Test
    void applies_creditAccountOpened_createsSummary() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = outbox(eventId, id, 1L, new CreditAccountOpened(Instant.parse("2025-01-01T00:00:00Z")));

        ProjectionTick tick = projector.project(event, Optional.empty());

        CreditAccountSummary summary = tick.summary();
        assertThat(summary).isNotNull();
        assertThat(summary.creditAccountId()).isEqualTo(id);
        assertThat(summary.opened()).isTrue();
        assertThat(summary.outstandingBalance()).isEqualTo("0.00");
        assertThat(summary.authorizedAmount()).isEqualTo("0.00");
        assertThat(summary.availableLimit()).isEqualTo("0.00");
        assertThat(summary.projectedVersion()).isEqualTo(1L);
        assertThat(tick.applied()).isTrue();
    }

    @Test
    void applies_creditLimitAssigned_setsLimit() {
        UUID id = UUID.randomUUID();
        OutboxEvent opened = outbox(UUID.randomUUID(), id, 1L, new CreditAccountOpened(Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(UUID.randomUUID(), id, 2L, new CreditLimitAssigned(Instant.parse("2025-01-01T00:00:01Z"), "1000.00"));

        CreditAccountSummary base = projector.project(opened, Optional.empty()).summary();
        ProjectionTick tick = projector.project(limit, Optional.of(base));

        assertThat(tick.summary().creditLimit()).isEqualTo("1000.00");
        assertThat(tick.summary().availableLimit()).isEqualTo("1000.00");
        assertThat(tick.summary().projectedVersion()).isEqualTo(2L);
    }

    @Test
    void applies_purchaseAuthorized_reducesAvailableAndAddsAuth() {
        UUID id = UUID.randomUUID();
        UUID authId = UUID.randomUUID();
        OutboxEvent opened = outbox(UUID.randomUUID(), id, 1L, new CreditAccountOpened(Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(UUID.randomUUID(), id, 2L, new CreditLimitAssigned(Instant.parse("2025-01-01T00:00:01Z"), "1000.00"));
        OutboxEvent auth = outbox(UUID.randomUUID(), id, 3L, new PurchaseAuthorized(Instant.parse("2025-01-01T00:00:02Z"), authId, "200.00", "Store"));

        CreditAccountSummary s1 = projector.project(opened, Optional.empty()).summary();
        CreditAccountSummary s2 = projector.project(limit, Optional.of(s1)).summary();
        ProjectionTick tick = projector.project(auth, Optional.of(s2));

        assertThat(tick.summary().authorizedAmount()).isEqualTo("200.00");
        assertThat(tick.summary().availableLimit()).isEqualTo("800.00");
        assertThat(tick.summary().authorizations()).hasSize(1);
        assertThat(tick.summary().authorizations().get(0).status()).isEqualTo("OPEN");
    }

    @Test
    void applies_capturePaymentRelease_payment() {
        UUID id = UUID.randomUUID();
        UUID authId = UUID.randomUUID();
        OutboxEvent opened = outbox(UUID.randomUUID(), id, 1L, new CreditAccountOpened(Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(UUID.randomUUID(), id, 2L, new CreditLimitAssigned(Instant.parse("2025-01-01T00:00:01Z"), "1000.00"));
        OutboxEvent auth = outbox(UUID.randomUUID(), id, 3L, new PurchaseAuthorized(Instant.parse("2025-01-01T00:00:02Z"), authId, "200.00", "Store"));
        OutboxEvent capture = outbox(UUID.randomUUID(), id, 4L, new PurchaseCaptured(Instant.parse("2025-01-01T00:00:03Z"), authId));

        CreditAccountSummary s1 = projector.project(opened, Optional.empty()).summary();
        CreditAccountSummary s2 = projector.project(limit, Optional.of(s1)).summary();
        CreditAccountSummary s3 = projector.project(auth, Optional.of(s2)).summary();
        ProjectionTick tick = projector.project(capture, Optional.of(s3));

        assertThat(tick.summary().outstandingBalance()).isEqualTo("200.00");
        assertThat(tick.summary().authorizedAmount()).isEqualTo("0.00");
        assertThat(tick.summary().authorizations().get(0).status()).isEqualTo("CAPTURED");
    }

    @Test
    void applies_paymentReceived_reducesOutstanding() {
        UUID id = UUID.randomUUID();
        UUID authId = UUID.randomUUID();
        OutboxEvent opened = outbox(UUID.randomUUID(), id, 1L, new CreditAccountOpened(Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(UUID.randomUUID(), id, 2L, new CreditLimitAssigned(Instant.parse("2025-01-01T00:00:01Z"), "1000.00"));
        OutboxEvent auth = outbox(UUID.randomUUID(), id, 3L, new PurchaseAuthorized(Instant.parse("2025-01-01T00:00:02Z"), authId, "200.00", "Store"));
        OutboxEvent capture = outbox(UUID.randomUUID(), id, 4L, new PurchaseCaptured(Instant.parse("2025-01-01T00:00:03Z"), authId));
        OutboxEvent payment = outbox(UUID.randomUUID(), id, 5L, new PaymentReceived(Instant.parse("2025-01-01T00:00:04Z"), "200.00"));

        CreditAccountSummary s1 = projector.project(opened, Optional.empty()).summary();
        CreditAccountSummary s2 = projector.project(limit, Optional.of(s1)).summary();
        CreditAccountSummary s3 = projector.project(auth, Optional.of(s2)).summary();
        CreditAccountSummary s4 = projector.project(capture, Optional.of(s3)).summary();
        ProjectionTick tick = projector.project(payment, Optional.of(s4));

        assertThat(tick.summary().outstandingBalance()).isEqualTo("0.00");
        assertThat(tick.summary().availableLimit()).isEqualTo("800.00");
    }

    @Test
    void idempotent_whenVersionAlreadyApplied() {
        UUID id = UUID.randomUUID();
        OutboxEvent opened = outbox(UUID.randomUUID(), id, 1L, new CreditAccountOpened(Instant.parse("2025-01-01T00:00:00Z")));

        CreditAccountSummary base = projector.project(opened, Optional.empty()).summary();
        ProjectionTick tick = projector.project(opened, Optional.of(base));

        assertThat(tick.applied()).isFalse();
        assertThat(tick.summary()).isEqualTo(base);
    }

    @Test
    void outOfOrder_event_isNotApplied() {
        UUID id = UUID.randomUUID();
        OutboxEvent opened = outbox(UUID.randomUUID(), id, 1L, new CreditAccountOpened(Instant.parse("2025-01-01T00:00:00Z")));
        OutboxEvent limit = outbox(UUID.randomUUID(), id, 3L, new CreditLimitAssigned(Instant.parse("2025-01-01T00:00:01Z"), "1000.00"));

        CreditAccountSummary base = projector.project(opened, Optional.empty()).summary();
        ProjectionTick tick = projector.project(limit, Optional.of(base));

        assertThat(tick.applied()).isFalse();
        assertThat(tick.summary()).isEqualTo(base);
    }

    private OutboxEvent outbox(UUID eventId, UUID accountId, long version, CreditAccountEvent event) {
        return new OutboxEvent(eventId, "CreditAccount", accountId.toString(), version, event.getClass().getSimpleName(), event, java.util.Map.of(), event.occurredAt());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests *CreditAccountSummaryProjectorTest`
Expected: FAIL (cannot find symbol CreditAccountSummaryProjector).

- [ ] **Step 3: Create `ProjectionTick` record**

```java
package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;

public record ProjectionTick(CreditAccountSummary summary, boolean applied) {}
```

- [ ] **Step 4: Implement `CreditAccountSummaryProjector`**

```java
package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.*;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Component
public class CreditAccountSummaryProjector {

    public ProjectionTick project(OutboxEvent event, Optional<CreditAccountSummary> current) {
        long eventVersion = event.aggregateVersion();
        if (current.isPresent()) {
            long projected = current.get().projectedVersion();
            if (projected >= eventVersion) {
                return new ProjectionTick(current.get(), false);
            }
            if (projected + 1 < eventVersion) {
                return new ProjectionTick(current.get(), false);
            }
        } else if (eventVersion != 1L) {
            return new ProjectionTick(null, false);
        }

        CreditAccountSummary base = current.orElseGet(() -> emptySummary(event));
        CreditAccountSummary next = apply(base, event.event());
        return new ProjectionTick(next, true);
    }

    private CreditAccountSummary apply(CreditAccountSummary s, CreditAccountEvent event) {
        if (event instanceof CreditAccountOpened opened) {
            return new CreditAccountSummary(
                    s.creditAccountId(),
                    true,
                    null,
                    "0.00",
                    "0.00",
                    "0.00",
                    List.of(),
                    s.projectedVersion() + 1,
                    eventIdOf(s, opened),
                    opened.occurredAt());
        }
        if (event instanceof CreditLimitAssigned assigned) {
            BigDecimal limit = new BigDecimal(assigned.limit());
            return new CreditAccountSummary(
                    s.creditAccountId(),
                    s.opened(),
                    assigned.limit(),
                    s.outstandingBalance(),
                    s.authorizedAmount(),
                    limit.subtract(new BigDecimal(s.outstandingBalance())).subtract(new BigDecimal(s.authorizedAmount())).toPlainString(),
                    s.authorizations(),
                    s.projectedVersion() + 1,
                    eventIdOf(s, assigned),
                    assigned.occurredAt());
        }
        if (event instanceof CreditLimitChanged changed) {
            BigDecimal newLimit = new BigDecimal(changed.newLimit());
            BigDecimal outstanding = new BigDecimal(s.outstandingBalance());
            BigDecimal authorized = new BigDecimal(s.authorizedAmount());
            String newAvailable = newLimit.subtract(outstanding).subtract(authorized).toPlainString();
            return new CreditAccountSummary(
                    s.creditAccountId(),
                    s.opened(),
                    changed.newLimit(),
                    s.outstandingBalance(),
                    s.authorizedAmount(),
                    newAvailable,
                    s.authorizations(),
                    s.projectedVersion() + 1,
                    eventIdOf(s, changed),
                    changed.occurredAt());
        }
        if (event instanceof PurchaseAuthorized auth) {
            BigDecimal amount = new BigDecimal(auth.amount());
            BigDecimal newAuthorized = new BigDecimal(s.authorizedAmount()).add(amount);
            BigDecimal newAvailable = new BigDecimal(s.availableLimit()).subtract(amount);
            List<CreditAccountSummary.AuthorizationSummary> auths = new ArrayList<>(s.authorizations());
            auths.add(new CreditAccountSummary.AuthorizationSummary(
                    UUID.fromString(auth.authorizationId().toString()),
                    auth.amount(),
                    "OPEN",
                    auth.merchantName()));
            return new CreditAccountSummary(
                    s.creditAccountId(),
                    s.opened(),
                    s.creditLimit(),
                    s.outstandingBalance(),
                    newAuthorized.toPlainString(),
                    newAvailable.toPlainString(),
                    auths,
                    s.projectedVersion() + 1,
                    eventIdOf(s, auth),
                    auth.occurredAt());
        }
        if (event instanceof PurchaseCaptured captured) {
            List<CreditAccountSummary.AuthorizationSummary> updated = s.authorizations().stream()
                    .map(a -> a.authorizationId().toString().equals(captured.authorizationId().toString())
                            ? new CreditAccountSummary.AuthorizationSummary(a.authorizationId(), a.amount(), "CAPTURED", a.merchantName())
                            : a)
                    .toList();
            BigDecimal capturedAmount = s.authorizations().stream()
                    .filter(a -> a.authorizationId().toString().equals(captured.authorizationId().toString()))
                    .map(a -> new BigDecimal(a.amount()))
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
            BigDecimal newOutstanding = new BigDecimal(s.outstandingBalance()).add(capturedAmount);
            return new CreditAccountSummary(
                    s.creditAccountId(),
                    s.opened(),
                    s.creditLimit(),
                    newOutstanding.toPlainString(),
                    s.authorizedAmount(),
                    s.availableLimit(),
                    updated,
                    s.projectedVersion() + 1,
                    eventIdOf(s, captured),
                    captured.occurredAt());
        }
        if (event instanceof PurchaseAuthorizationReleased released) {
            List<CreditAccountSummary.AuthorizationSummary> updated = s.authorizations().stream()
                    .map(a -> a.authorizationId().toString().equals(released.authorizationId().toString())
                            ? new CreditAccountSummary.AuthorizationSummary(a.authorizationId(), a.amount(), "RELEASED", a.merchantName())
                            : a)
                    .toList();
            BigDecimal releasedAmount = s.authorizations().stream()
                    .filter(a -> a.authorizationId().toString().equals(released.authorizationId().toString()))
                    .map(a -> new BigDecimal(a.amount()))
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
            BigDecimal newAuthorized = new BigDecimal(s.authorizedAmount()).subtract(releasedAmount);
            BigDecimal newAvailable = new BigDecimal(s.availableLimit()).add(releasedAmount);
            return new CreditAccountSummary(
                    s.creditAccountId(),
                    s.opened(),
                    s.creditLimit(),
                    s.outstandingBalance(),
                    newAuthorized.toPlainString(),
                    newAvailable.toPlainString(),
                    updated,
                    s.projectedVersion() + 1,
                    eventIdOf(s, released),
                    released.occurredAt());
        }
        if (event instanceof PaymentReceived payment) {
            BigDecimal newOutstanding = new BigDecimal(s.outstandingBalance()).subtract(new BigDecimal(payment.amount()));
            BigDecimal newAvailable = new BigDecimal(s.availableLimit()).add(new BigDecimal(payment.amount()));
            return new CreditAccountSummary(
                    s.creditAccountId(),
                    s.opened(),
                    s.creditLimit(),
                    newOutstanding.toPlainString(),
                    s.authorizedAmount(),
                    newAvailable.toPlainString(),
                    s.authorizations(),
                    s.projectedVersion() + 1,
                    eventIdOf(s, payment),
                    payment.occurredAt());
        }
        return s;
    }

    private UUID eventIdOf(CreditAccountSummary s, CreditAccountEvent event) {
        return s.lastEventId();
    }

    private CreditAccountSummary emptySummary(OutboxEvent event) {
        UUID id = UUID.fromString(event.aggregateId());
        return new CreditAccountSummary(
                id, false, null, "0.00", "0.00", "0.00",
                List.of(), 0L, event.eventId(), Instant.now());
    }
}
```

Note: `eventIdOf` returns the existing `lastEventId` placeholder; the worker will overwrite `lastEventId` with the actual `OutboxEvent.eventId()` after this method returns. To support that, change `apply` to return a tuple `(summary, lastEventId)` and have the worker set the `lastEventId` on the resulting summary. Adjust the projector accordingly:

```java
public record ProjectionTick(CreditAccountSummary summary, boolean applied) {}
```

Update `apply` to return `ProjectionTick` with a sentinel and have the worker overwrite `lastEventId`. Or add a new field to `ProjectionTick`:

```java
public record ProjectionTick(CreditAccountSummary summary, boolean applied, UUID lastEventId) {}
```

Update the tests to assert against `tick.lastEventId()`. The projector sets `lastEventId` to `null` and the worker overwrites it with the outbox row's `eventId`.

Adjust the implementation so that `apply` no longer touches `lastEventId` (the record's `lastEventId` field is overwritten by the worker). Make `lastEventId` a separate concern: the worker sets it after applying.

Final approach used by tests: `tick.lastEventId()` returns the `OutboxEvent.eventId()` that was just applied. The projector ignores `s.lastEventId()`; only the worker overwrites it.

Update the implementation to not pass `s.lastEventId()` at all. The constructor of `CreditAccountSummary` will receive `null` and the worker will set it via `upsert`.

- [ ] **Step 5: Run unit tests**

Run: `./gradlew test --tests *CreditAccountSummaryProjectorTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjector.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionTick.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java
git commit -m "feat(projection): implement CreditAccountSummaryProjector"
```

---

## Task 11: Implement `ProjectionWorker` and configuration

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/projection/ProjectionProperties.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/scheduler/OutboxProjectionWorkerRunner.java`
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java`

- [ ] **Step 1: Create `ProjectionProperties`**

```java
package com.sanmoo.eventsourcing.creditaccount.projection;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "credit-account.projections")
public class ProjectionProperties {
    private boolean enabled = true;
    private java.time.Duration pollInterval = java.time.Duration.ofSeconds(1);
    private int batchSize = 50;
    private int maxAttempts = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public java.time.Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(java.time.Duration pollInterval) { this.pollInterval = pollInterval; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
}
```

- [ ] **Step 2: Implement `ProjectionWorker`**

```java
package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectionWorker {

    private final OutboxEventRepository outbox;
    private final CreditAccountSummaryRepository summaries;
    private final CreditAccountSummaryProjector projector;
    private final ProjectionProperties properties;

    public int processOnce() {
        List<OutboxEvent> pending = outbox.findPending(properties.getBatchSize());
        int processed = 0;
        for (OutboxEvent event : pending) {
            try {
                processOne(event);
                processed++;
            } catch (RuntimeException e) {
                outbox.markFailed(event.eventId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return processed;
    }

    @Transactional
    public void processOne(OutboxEvent event) {
        CreditAccountId id = CreditAccountId.of(UUID.fromString(event.aggregateId()));
        Optional<CreditAccountSummary> current = summaries.findById(id);
        ProjectionTick tick = projector.project(event, current);
        if (!tick.applied() || tick.summary() == null) {
            return;
        }
        CreditAccountSummary withEventId = new CreditAccountSummary(
                tick.summary().creditAccountId(),
                tick.summary().opened(),
                tick.summary().creditLimit(),
                tick.summary().outstandingBalance(),
                tick.summary().authorizedAmount(),
                tick.summary().availableLimit(),
                tick.summary().authorizations(),
                tick.summary().projectedVersion(),
                event.eventId(),
                tick.summary().updatedAt());
        summaries.upsert(withEventId);
        outbox.markProcessed(event.eventId());
    }
}
```

- [ ] **Step 3: Implement `OutboxProjectionWorkerRunner`**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.scheduler;

import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "credit-account.projections", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OutboxProjectionWorkerRunner {

    private final ProjectionWorker worker;
    private final ProjectionProperties properties;

    @Scheduled(fixedDelayString = "${credit-account.projections.poll-interval:1s}")
    public void run() {
        try {
            int processed = worker.processOnce();
            if (processed > 0) {
                log.debug("Projection worker processed {} events", processed);
            }
        } catch (RuntimeException e) {
            log.error("Projection worker tick failed", e);
        }
    }
}
```

- [ ] **Step 4: Update `application.yml`**

Append:

```yaml
credit-account:
  projections:
    enabled: true
    poll-interval: 1s
    batch-size: 50
    max-attempts: 10
```

- [ ] **Step 5: Add `@EnableConfigurationProperties` registration**

Modify `CreditAccountApplication.java`:

```java
package com.sanmoo.eventsourcing.creditaccount;

import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ProjectionProperties.class)
public class CreditAccountApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreditAccountApplication.class, args);
    }
}
```

- [ ] **Step 6: Write a worker test**

In `ProjectionWorkerTest`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxEventRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectionWorkerTest {

    @Test
    void processOnce_appliesPendingEvents() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        CreditAccountSummaryRepository summaries = mock(CreditAccountSummaryRepository.class);
        CreditAccountSummaryProjector projector = new CreditAccountSummaryProjector();
        ProjectionProperties props = new ProjectionProperties();
        props.setBatchSize(10);

        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, "CreditAccount", accountId.toString(), 1L,
                "CreditAccountOpened", new CreditAccountOpened(Instant.now()),
                java.util.Map.of(), Instant.now());

        when(outbox.findPending(10)).thenReturn(List.of(event));
        when(summaries.findById(CreditAccountId.of(accountId))).thenReturn(Optional.empty());

        ProjectionWorker worker = new ProjectionWorker(outbox, summaries, projector, props);
        int processed = worker.processOnce();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<CreditAccountSummary> captor = ArgumentCaptor.forClass(CreditAccountSummary.class);
        verify(summaries).upsert(captor.capture());
        assertThat(captor.getValue().creditAccountId()).isEqualTo(accountId);
        verify(outbox).markProcessed(eventId);
    }

    @Test
    void processOnce_recordsFailureOnException() {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        CreditAccountSummaryRepository summaries = mock(CreditAccountSummaryRepository.class);
        CreditAccountSummaryProjector projector = mock(CreditAccountSummaryProjector.class);
        ProjectionProperties props = new ProjectionProperties();
        props.setBatchSize(10);

        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, "CreditAccount", accountId.toString(), 1L,
                "CreditAccountOpened", new CreditAccountOpened(Instant.now()),
                java.util.Map.of(), Instant.now());

        when(outbox.findPending(10)).thenReturn(List.of(event));
        when(summaries.findById(any())).thenReturn(Optional.empty());
        when(projector.project(any(), any())).thenThrow(new RuntimeException("boom"));

        ProjectionWorker worker = new ProjectionWorker(outbox, summaries, projector, props);
        int processed = worker.processOnce();

        assertThat(processed).isEqualTo(0);
        verify(outbox).markFailed(eq(eventId), contains("boom"));
    }
}
```

- [ ] **Step 7: Run worker tests**

Run: `./gradlew test --tests *ProjectionWorkerTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/projection/ProjectionProperties.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/scheduler/OutboxProjectionWorkerRunner.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/CreditAccountApplication.java \
        src/main/resources/application.yml \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java
git commit -m "feat(projection): wire scheduler and ProjectionWorker"
```

---

## Task 12: Switch `GetCreditAccountUseCase` to read from summary

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/SummaryNotFoundException.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/ProjectionNotReadyException.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/CreditAccountOutput.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/GetCreditAccountInput.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCaseTest.java`

- [ ] **Step 1: Create errors**

`SummaryNotFoundException`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.error;

public class SummaryNotFoundException extends RuntimeException {
    public SummaryNotFoundException(String message) { super(message); }
}
```

`ProjectionNotReadyException`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.error;

public class ProjectionNotReadyException extends RuntimeException {
    private final java.util.UUID creditAccountId;
    private final Long currentProjectionVersion;
    private final long requiredVersion;

    public ProjectionNotReadyException(java.util.UUID creditAccountId, Long currentProjectionVersion, long requiredVersion) {
        super("Projection not ready for " + creditAccountId);
        this.creditAccountId = creditAccountId;
        this.currentProjectionVersion = currentProjectionVersion;
        this.requiredVersion = requiredVersion;
    }

    public java.util.UUID getCreditAccountId() { return creditAccountId; }
    public Long getCurrentProjectionVersion() { return currentProjectionVersion; }
    public long getRequiredVersion() { return requiredVersion; }
}
```

- [ ] **Step 2: Update `CreditAccountOutput` to add `projectedVersion`**

Add a final field `projectedVersion` (nullable Long):

```java
public record CreditAccountOutput(
        String creditAccountId,
        boolean opened,
        String creditLimit,
        String outstandingBalance,
        String authorizedAmount,
        String availableLimit,
        List<PurchaseAuthorizationOutput> authorizations,
        Long projectedVersion
) {}
```

Update the constructor in `CreditAccountUseCaseSupport.buildOutput(...)` to set `projectedVersion` to `null` for command responses. Commands still don't know projected version.

In `CreditAccountUseCaseSupport.buildOutput`, add at the end of the constructor call:

```java
null
```

- [ ] **Step 3: Update `GetCreditAccountInput` to add `minVersion`**

```java
public record GetCreditAccountInput(CreditAccountId creditAccountId, Long minVersion) {}
```

Provide a static factory:

```java
public static GetCreditAccountInput of(CreditAccountId id) { return new GetCreditAccountInput(id, null); }
public static GetCreditAccountInput of(CreditAccountId id, Long minVersion) { return new GetCreditAccountInput(id, minVersion); }
```

- [ ] **Step 4: Rewrite `GetCreditAccountUseCase` to read from summary**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.ProjectionNotReadyException;
import com.sanmoo.eventsourcing.creditaccount.core.error.SummaryNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.PurchaseAuthorizationOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetCreditAccountUseCase {

    private final CreditAccountSummaryRepository summaries;

    public GetCreditAccountOutput execute(GetCreditAccountInput input) {
        Optional<CreditAccountSummary> maybe = summaries.findById(input.creditAccountId());
        if (maybe.isEmpty()) {
            if (input.minVersion() != null) {
                throw new ProjectionNotReadyException(input.creditAccountId().value(), null, input.minVersion());
            }
            throw new SummaryNotFoundException("Credit account not found: " + input.creditAccountId().value());
        }
        CreditAccountSummary summary = maybe.get();
        if (input.minVersion() != null && summary.projectedVersion() < input.minVersion()) {
            throw new ProjectionNotReadyException(input.creditAccountId().value(), summary.projectedVersion(), input.minVersion());
        }
        return new GetCreditAccountOutput(toOutput(summary));
    }

    private CreditAccountOutput toOutput(CreditAccountSummary s) {
        List<PurchaseAuthorizationOutput> auths = s.authorizations().stream()
                .map(a -> new PurchaseAuthorizationOutput(
                        a.authorizationId().toString(),
                        a.amount(),
                        a.status(),
                        a.merchantName()))
                .toList();
        return new CreditAccountOutput(
                s.creditAccountId().toString(),
                s.opened(),
                s.creditLimit(),
                s.outstandingBalance(),
                s.authorizedAmount(),
                s.availableLimit(),
                auths,
                s.projectedVersion());
    }
}
```

- [ ] **Step 5: Update the unit test**

Replace the body of `GetCreditAccountUseCaseTest` with:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.ProjectionNotReadyException;
import com.sanmoo.eventsourcing.creditaccount.core.error.SummaryNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetCreditAccountUseCaseTest {

    private final CreditAccountSummaryRepository repo = mock(CreditAccountSummaryRepository.class);
    private final GetCreditAccountUseCase useCase = new GetCreditAccountUseCase(repo);

    @Test
    void returnsSummary_whenFound_andNoMinVersion() {
        UUID id = UUID.randomUUID();
        CreditAccountSummary summary = new CreditAccountSummary(
                id, true, "1000.00", "0.00", "0.00", "1000.00", List.of(), 3L, UUID.randomUUID(), Instant.now());
        when(repo.findById(CreditAccountId.of(id))).thenReturn(Optional.of(summary));

        var output = useCase.execute(new GetCreditAccountInput(CreditAccountId.of(id), null));

        assertThat(output.account().projectedVersion()).isEqualTo(3L);
    }

    @Test
    void throwsSummaryNotFound_whenMissing_andNoMinVersion() {
        UUID id = UUID.randomUUID();
        when(repo.findById(CreditAccountId.of(id))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountInput(CreditAccountId.of(id), null)))
                .isInstanceOf(SummaryNotFoundException.class);
    }

    @Test
    void throwsProjectionNotReady_whenMissing_andMinVersionProvided() {
        UUID id = UUID.randomUUID();
        when(repo.findById(CreditAccountId.of(id))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountInput(CreditAccountId.of(id), 5L)))
                .isInstanceOf(ProjectionNotReadyException.class)
                .extracting("currentProjectionVersion", "requiredVersion")
                .containsExactly(null, 5L);
    }

    @Test
    void throwsProjectionNotReady_whenProjectionBehind() {
        UUID id = UUID.randomUUID();
        CreditAccountSummary summary = new CreditAccountSummary(
                id, true, "1000.00", "0.00", "0.00", "1000.00", List.of(), 2L, UUID.randomUUID(), Instant.now());
        when(repo.findById(CreditAccountId.of(id))).thenReturn(Optional.of(summary));

        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountInput(CreditAccountId.of(id), 5L)))
                .isInstanceOf(ProjectionNotReadyException.class);
    }

    @Test
    void returnsSummary_whenProjectionMeetsMinVersion() {
        UUID id = UUID.randomUUID();
        CreditAccountSummary summary = new CreditAccountSummary(
                id, true, "1000.00", "0.00", "0.00", "1000.00", List.of(), 5L, UUID.randomUUID(), Instant.now());
        when(repo.findById(CreditAccountId.of(id))).thenReturn(Optional.of(summary));

        var output = useCase.execute(new GetCreditAccountInput(CreditAccountId.of(id), 5L));

        assertThat(output.account().projectedVersion()).isEqualTo(5L);
    }
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew test --tests *GetCreditAccountUseCaseTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/SummaryNotFoundException.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/ProjectionNotReadyException.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/CreditAccountOutput.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/GetCreditAccountInput.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCaseTest.java
git commit -m "feat(query): GetCreditAccountUseCase reads from summary"
```

---

## Task 13: Add `ListCreditAccountsUseCase` and DTOs

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ListCreditAccountsInput.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ListCreditAccountsOutput.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCase.java`
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCaseTest.java`

- [ ] **Step 1: Create input record**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

public record ListCreditAccountsInput(int page, int size) {
    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public ListCreditAccountsInput {
        if (size <= 0) size = DEFAULT_SIZE;
    }
}
```

- [ ] **Step 2: Create output record**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;

public record ListCreditAccountsOutput(CreditAccountSummaryPage page) {}
```

- [ ] **Step 3: Create use case**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidPageSizeException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ListCreditAccountsInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ListCreditAccountsOutput;
import com.sanmoo.eventsourcing.creditaccount.domain.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListCreditAccountsUseCase {

    private final CreditAccountSummaryRepository summaries;

    public ListCreditAccountsOutput execute(ListCreditAccountsInput input) {
        if (input.size() > ListCreditAccountsInput.MAX_SIZE) {
            throw new InvalidPageSizeException("size must be <= " + ListCreditAccountsInput.MAX_SIZE);
        }
        if (input.page() < 0) {
            throw new InvalidPageSizeException("page must be >= 0");
        }
        CreditAccountSummaryPage page = summaries.findAll(new CreditAccountSummaryPageRequest(input.page(), input.size()));
        return new ListCreditAccountsOutput(page);
    }
}
```

- [ ] **Step 4: Create `InvalidPageSizeException`**

```java
package com.sanmoo.eventsourcing.creditaccount.core.error;

import com.sanmoo.eventsourcing.creditaccount.domain.error.DomainException;

public class InvalidPageSizeException extends DomainException {
    public InvalidPageSizeException(String message) { super(message); }
}
```

- [ ] **Step 5: Write use case test**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidPageSizeException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ListCreditAccountsInput;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListCreditAccountsUseCaseTest {

    private final CreditAccountSummaryRepository repo = mock(CreditAccountSummaryRepository.class);
    private final ListCreditAccountsUseCase useCase = new ListCreditAccountsUseCase(repo);

    @Test
    void rejects_sizeAboveMax() {
        assertThatThrownBy(() -> useCase.execute(new ListCreditAccountsInput(0, 200)))
                .isInstanceOf(InvalidPageSizeException.class);
    }

    @Test
    void rejects_negativePage() {
        assertThatThrownBy(() -> useCase.execute(new ListCreditAccountsInput(-1, 20)))
                .isInstanceOf(InvalidPageSizeException.class);
    }

    @Test
    void delegatesToRepository_withDefaults() {
        when(repo.findAll(any())).thenReturn(new CreditAccountSummaryPage(List.of(), 0, 20, 0, 0));

        useCase.execute(new ListCreditAccountsInput(0, 20));

        ArgumentCaptor<CreditAccountSummaryPageRequest> captor = ArgumentCaptor.forClass(CreditAccountSummaryPageRequest.class);
        verify(repo).findAll(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(0);
        assertThat(captor.getValue().size()).isEqualTo(20);
    }
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew test --tests *ListCreditAccountsUseCaseTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ListCreditAccountsInput.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ListCreditAccountsOutput.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCase.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/InvalidPageSizeException.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCaseTest.java
git commit -m "feat(query): add ListCreditAccountsUseCase"
```

---

## Task 14: Wire controller endpoints and error handler

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/PageResponse.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/ProjectionNotReadyResponse.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestExceptionHandler.java`
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerListIT.java`

- [ ] **Step 1: Create `PageResponse` record**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.util.List;
import java.util.Map;

public record PageResponse(
        List<Map<String, Object>> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
```

- [ ] **Step 2: Create `ProjectionNotReadyResponse` record**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.util.UUID;

public record ProjectionNotReadyResponse(
        String message,
        UUID creditAccountId,
        Long currentProjectionVersion,
        long requiredVersion
) {}
```

- [ ] **Step 3: Add list endpoint and `minVersion` handling to controller**

Modify `CreditAccountController` to add fields and endpoints:

```java
private final ListCreditAccountsUseCase listCreditAccountsUseCase;
```

Add new imports for `GetCreditAccountInput`, `ProjectionNotReadyException`, `SummaryNotFoundException`, `ListCreditAccountsInput`, `ListCreditAccountsOutput`, `CreditAccountSummaryPage`, `PageResponse`, `ProjectionNotReadyResponse`.

Replace `getAccount`:

```java
@GetMapping("/{id}")
public ResponseEntity<?> getAccount(
        @PathVariable String id,
        @RequestParam(value = "minVersion", required = false) Long minVersion) {
    var creditAccountId = CreditAccountId.of(UUID.fromString(id));
    var input = minVersion == null
            ? GetCreditAccountInput.of(creditAccountId)
            : GetCreditAccountInput.of(creditAccountId, minVersion);
    var output = getCreditAccountUseCase.execute(input);
    return ResponseEntity.ok(toMap(output.account()));
}
```

Add list endpoint:

```java
@GetMapping
public ResponseEntity<PageResponse> listAccounts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    ListCreditAccountsOutput output = listCreditAccountsUseCase.execute(new ListCreditAccountsInput(page, size));
    CreditAccountSummaryPage p = output.page();
    List<Map<String, Object>> items = p.items().stream()
            .map(this::summaryToMap)
            .toList();
    PageResponse body = new PageResponse(items, p.page(), p.size(), p.totalItems(), p.totalPages());
    return ResponseEntity.ok(body);
}

private Map<String, Object> summaryToMap(CreditAccountSummary s) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("creditAccountId", s.creditAccountId().toString());
    data.put("opened", s.opened());
    data.put("creditLimit", s.creditLimit());
    data.put("outstandingBalance", s.outstandingBalance());
    data.put("authorizedAmount", s.authorizedAmount());
    data.put("availableLimit", s.availableLimit());
    data.put("projectedVersion", s.projectedVersion());
    data.put("authorizations", s.authorizations().stream().map(a -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("authorizationId", a.authorizationId().toString());
        m.put("amount", a.amount());
        m.put("status", a.status());
        m.put("merchantName", a.merchantName());
        return m;
    }).toList());
    return data;
}
```

- [ ] **Step 4: Update `RestExceptionHandler`**

Add handlers:

```java
@ExceptionHandler(SummaryNotFoundException.class)
public ResponseEntity<Map<String, Object>> handleSummaryNotFound(SummaryNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not Found", "message", ex.getMessage()));
}

@ExceptionHandler(ProjectionNotReadyException.class)
public ResponseEntity<ProjectionNotReadyResponse> handleProjectionNotReady(ProjectionNotReadyException ex) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ProjectionNotReadyResponse(
            "Projection not ready", ex.getCreditAccountId(), ex.getCurrentProjectionVersion(), ex.getRequiredVersion()));
}

@ExceptionHandler(InvalidPageSizeException.class)
public ResponseEntity<Map<String, Object>> handleInvalidPageSize(InvalidPageSizeException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Bad Request", "message", ex.getMessage()));
}
```

- [ ] **Step 5: Write controller list integration test**

In `CreditAccountControllerListIT`:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.CreditAccountApplication;
import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapter;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(classes = {CreditAccountApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CreditAccountControllerListIT {

    @Autowired WebTestClient webTestClient;
    @Autowired JdbcEventStoreAdapter eventStoreAdapter;
    @Autowired ProjectionWorker projectionWorker;

    @Test
    void getById_returns404_whenSummaryMissing() {
        UUID id = UUID.randomUUID();
        webTestClient.get().uri("/credit-accounts/{id}", id).exchange().expectStatus().isNotFound();
    }

    @Test
    void getById_returns200_withProjectedVersion() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(Instant.now())), Map.of());
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 1,
                List.of(new CreditLimitAssigned(Instant.now(), "1000.00")), Map.of());
        projectionWorker.processOnce();

        webTestClient.get().uri("/credit-accounts/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.creditAccountId").isEqualTo(id.toString())
                .jsonPath("$.projectedVersion").isEqualTo(2);
    }

    @Test
    void getById_returns202_whenMinVersionNotReached() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(Instant.now())), Map.of());
        // do not run projection worker

        webTestClient.get().uri(uri -> uri.path("/credit-accounts/{id}").queryParam("minVersion", 5).build(id))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.requiredVersion").isEqualTo(5);
    }

    @Test
    void list_returns400_whenSizeAboveMax() {
        webTestClient.get().uri(uri -> uri.path("/credit-accounts").queryParam("size", 200).build())
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void list_returnsPagedItems() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(Instant.now())), Map.of());
        projectionWorker.processOnce();

        webTestClient.get().uri("/credit-accounts")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].creditAccountId").isEqualTo(id.toString());
    }
}
```

- [ ] **Step 6: Run the controller test**

Run: `./gradlew test --tests *CreditAccountControllerListIT`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/PageResponse.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/ProjectionNotReadyResponse.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestExceptionHandler.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerListIT.java
git commit -m "feat(rest): add list endpoint and minVersion support"
```

---

## Task 15: Update README and scripts

**Files:**
- Modify: `README.md`
- Modify: scripts in `scripts/` (just add a new `list-accounts` script)

- [ ] **Step 1: Update README**

Replace the "Key Design Decisions" list with:

```markdown
- **Manual event sourcing** with PostgreSQL `event_store` table (JSONB payload)
- **Outbox pattern**: each appended event also writes a row to `outbox_events` in the same transaction
- **Asynchronous projection**: a scheduled worker applies outbox events to `credit_account_summary`
- **Query side reads only the read model**: `GET` endpoints no longer rehydrate the aggregate
- **`minVersion` query parameter** for read-your-writes consistency
- **Optimistic locking** via `UNIQUE(aggregate_id, aggregate_version)`
- **Idempotent commands** via `Idempotency-Key` header and idempotency store
```

Update REST endpoints table:

```markdown
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/credit-accounts` | Open a credit account |
| `POST` | `/credit-accounts/{id}/credit-limit` | Assign or change credit limit |
| `POST` | `/credit-accounts/{id}/purchases/authorizations` | Authorize a purchase |
| `POST` | `/credit-accounts/{id}/purchases/authorizations/{authId}/capture` | Capture an authorization |
| `POST` | `/credit-accounts/{id}/purchases/authorizations/{authId}/release` | Release an authorization |
| `POST` | `/credit-accounts/{id}/payments` | Receive a payment |
| `GET` | `/credit-accounts` | List account summaries (paginated) |
| `GET` | `/credit-accounts/{id}` | Get account summary; `?minVersion=N` enables read-your-writes |
```

- [ ] **Step 2: Add `scripts/list-accounts` script**

```bash
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/api.sh"
PAGE="${1:-0}"
SIZE="${2:-20}"
api_get "/credit-accounts?page=${PAGE}&size=${SIZE}"
```

- [ ] **Step 3: Make script executable and commit**

```bash
chmod +x scripts/list-accounts
git add README.md scripts/list-accounts
git commit -m "docs: document async read model and list endpoint"
```

---

## Task 16: Run full test suite and verify quality gates

**Files:**
- Modify: any failing tests from prior tasks.

- [ ] **Step 1: Run unit and integration tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run quality tests**

Run: `./gradlew qualityTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: If any tests fail, fix them iteratively**

Address failures task-by-task, re-running `./gradlew test` after each fix. Commit fixes with descriptive messages.

- [ ] **Step 4: Final commit (if any follow-up changes)**

```bash
git add -A
git commit -m "chore: stabilize test suite after read model migration"
```

---

## Spec Coverage Checklist

- Event store + outbox written in same transaction → Task 4.
- `outbox_events` schema → Task 2.
- `credit_account_summary` schema → Task 3.
- `OutboxEventRepository` port (no `save`) → Tasks 5, 7, 8.
- `CreditAccountSummaryRepository` port with paging DTOs → Tasks 5, 6, 7, 9.
- Projector with idempotency and ordering rules → Task 10.
- Worker + scheduler + config → Task 11.
- Renaming `EventStorePort` → `EventStore`, `IdempotencyPort` → `IdempotencyRepository` → Task 1.
- `GET` without `minVersion` returns 200/404 → Task 12, 14.
- `GET` with `minVersion` returns 200/202 → Task 12, 14.
- `GET /credit-accounts` paginated list → Tasks 13, 14.
- Command responses unchanged (post-command state) → Task 12.
- Testing strategy → Tasks 8, 9, 10, 11, 12, 13, 14, 16.
- README updates → Task 15.
