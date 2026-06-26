# Global Event Position — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a monotonically increasing `global_position` column to `event_store` with read support in `EventEnvelope` and a new `loadEventsAfter` query method.

**Architecture:** PostgreSQL `BIGINT GENERATED ALWAYS AS IDENTITY` column on `event_store`. `JdbcEventStoreAdapter` reads it into `EventEnvelope` and exposes `loadEventsAfter(globalPosition, limit)`. `AppendResult` and `outbox_events` are unchanged.

## Global Constraints

- Java 25, Spring Boot 4.0.6, PostgreSQL 18
- All quality gates pass: checkstyle (max errors/warnings = 0), PMD, SpotBugs, ErrorProne, PiTest (≥80%)
- Existing aggregate rehydration (`loadEvents`) must continue working unchanged
- Use `BIGINT GENERATED ALWAYS AS IDENTITY` (not `BIGSERIAL`)
- `AppendResult` record is NOT modified

---

### Task 1: Add Liquibase Migration

**Files:**

- Create: `src/main/resources/db/changelog/011-add-global-position.yaml`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml`

**Interfaces:**

- Produces: `event_store.global_position` column (BIGINT, NOT NULL, UNIQUE, indexed, GENERATED ALWAYS AS IDENTITY)

- [ ] **Step 1: Write migration file**

```yaml
databaseChangeLog:
  - changeSet:
      id: 011-add-global-position
      author: sanmoo
      changes:
        - addColumn:
            tableName: event_store
            columns:
              - column:
                  name: global_position
                  type: BIGINT
                  constraints:
                    nullable: false
        - sql:
            sql: >
              ALTER TABLE event_store
              ALTER COLUMN global_position
              ADD GENERATED ALWAYS AS IDENTITY
        - addUniqueConstraint:
            tableName: event_store
            columnNames: global_position
            constraintName: uq_event_store_global_position
        - createIndex:
            tableName: event_store
            indexName: idx_event_store_global_position
            columns:
              - column:
                  name: global_position
```

- [ ] **Step 2: Register migration in master changelog**

In `src/main/resources/db/changelog/db.changelog-master.yaml`, append after the `010` include:

```yaml
  - include:
      file: db/changelog/011-add-global-position.yaml
```

- [ ] **Step 3: Build to verify YAML is valid Liquibase**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (Liquibase validates changelog YAML at startup)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/changelog/011-add-global-position.yaml \
        src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat: add global_position identity column to event_store"
```

---

### Task 2: Update EventEnvelope Model

**Files:**

- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/EventEnvelope.java`

**Interfaces:**

- Consumes: *(none from prior tasks — pure model change)*
- Produces: `EventEnvelope` record with new `long globalPosition` field

- [ ] **Step 1: Add globalPosition field to EventEnvelope**

Replace the record definition with:

```java
public record EventEnvelope(
    UUID eventId,
    String aggregateType,
    String aggregateId,
    long aggregateVersion,
    long globalPosition,
    CreditAccountEvent event,
    Instant occurredAt,
    Map<String, String> metadata
) {}
```

- [ ] **Step 2: Compile to find all broken call sites**

Run: `./gradlew compileJava compileTestJava`
Expected: COMPILATION FAILURE — `JdbcEventStoreAdapter.mapEventEnvelope()` will fail because it constructs `EventEnvelope` without the new field. This is expected; we fix it in Task 4.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/EventEnvelope.java
git commit -m "feat: add globalPosition field to EventEnvelope record"
```

---

### Task 3: Update EventStore Interface

**Files:**

- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/EventStore.java`

**Interfaces:**

- Consumes: `EventEnvelope` (updated in Task 2)
- Produces: `EventStore.loadEventsAfter(long globalPosition, int limit): List<EventEnvelope>`

- [ ] **Step 1: Add loadEventsAfter method to interface**

Add to the interface body (after `appendEvents`):

```java
List<EventEnvelope> loadEventsAfter(long globalPosition, int limit);
```

Full file should be:

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import java.util.List;
import java.util.Map;

public interface EventStore {
    List<EventEnvelope> loadEvents(String aggregateType, String aggregateId);
    AppendResult appendEvents(String aggregateType, String aggregateId, long expectedVersion, List<CreditAccountEvent> events, Map<String, String> metadata);
    List<EventEnvelope> loadEventsAfter(long globalPosition, int limit);
}
```

- [ ] **Step 2: Compile to verify interface-only change is valid**

Run: `./gradlew compileJava`
Expected: COMPILATION FAILURE (JdbcEventStoreAdapter doesn't implement `loadEventsAfter` yet — expected, fixed in Task 4)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/EventStore.java
git commit -m "feat: add loadEventsAfter method to EventStore interface"
```

---

### Task 4: Update JdbcEventStoreAdapter

**Files:**

- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java`

**Interfaces:**

- Consumes: `EventEnvelope` (Task 2), `EventStore` (Task 3), `event_store` table with `global_position` (Task 1)
- Produces: Working `loadEventsAfter` implementation, updated `loadEvents` that includes `globalPosition`

- [ ] **Step 1: Update LOAD_EVENTS_SQL to include global_position**

Replace:

```java
    private static final String LOAD_EVENTS_SQL = """
            SELECT event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, metadata, occurred_at
            FROM event_store
            WHERE aggregate_type = ? AND aggregate_id = ?
            ORDER BY aggregate_version ASC
            """;
```

With:

```java
    private static final String LOAD_EVENTS_SQL = """
            SELECT event_id, aggregate_type, aggregate_id, aggregate_version, global_position, event_type, payload, metadata, occurred_at
            FROM event_store
            WHERE aggregate_type = ? AND aggregate_id = ?
            ORDER BY aggregate_version ASC
            """;
```

- [ ] **Step 2: Add LOAD_EVENTS_AFTER_SQL constant**

After `LOAD_EVENTS_SQL`, add:

```java
    private static final String LOAD_EVENTS_AFTER_SQL = """
            SELECT event_id, aggregate_type, aggregate_id, aggregate_version, global_position, event_type, payload, metadata, occurred_at
            FROM event_store
            WHERE global_position > ?
            ORDER BY global_position ASC
            LIMIT ?
            """;
```

- [ ] **Step 3: Update mapEventEnvelope to read global_position**

In `mapEventEnvelope`, after `long aggregateVersion = rs.getLong("aggregate_version");`, add:

```java
        long globalPosition = rs.getLong("global_position");
```

And update the return statement to include the new field:

```java
        return new EventEnvelope(eventId, aggregateType, aggregateId, aggregateVersion, globalPosition, event, occurredAt, metadata);
```

- [ ] **Step 4: Implement loadEventsAfter method**

After `loadEvents`, add:

```java
    @Override
    @Transactional(readOnly = true)
    public List<EventEnvelope> loadEventsAfter(long globalPosition, int limit) {
        return jdbcTemplate.query(LOAD_EVENTS_AFTER_SQL, rowMapper, globalPosition, limit);
    }
```

- [ ] **Step 5: Compile to verify everything wires together**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java
git commit -m "feat: implement loadEventsAfter with global_position support in JdbcEventStoreAdapter"
```

---

### Task 5: Add Integration Tests

**Files:**

- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java`

**Interfaces:**

- Consumes: `EventStore` with `loadEventsAfter` (Task 4), `event_store` with `global_position` (Task 1)
- Produces: Test coverage for all acceptance criteria

- [ ] **Step 1: Add test — global_position populated on append**

Add to `JdbcEventStoreAdapterIT`:

```java
    @Test
    void globalPositionIsPopulatedOnAppend() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());
        var occurredAt = Instant.now();

        // when — append 3 events across 2 aggregates
        eventStore.appendEvents(
                aggregateType, aggregateId, 0,
                List.of(new CreditAccountOpened(creditAccountId, occurredAt)),
                Map.of());
        eventStore.appendEvents(
                aggregateType, aggregateId, 1,
                List.of(new CreditAccountOpened(creditAccountId, occurredAt)),
                Map.of());
        var otherId = UUID.randomUUID().toString();
        eventStore.appendEvents(
                aggregateType, otherId, 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(UUID.randomUUID()), Instant.now())),
                Map.of());

        // then
        List<Long> positions = jdbcTemplate.queryForList(
                "SELECT global_position FROM event_store ORDER BY global_position ASC",
                Long.class);
        assertThat(positions).hasSize(3);
        assertThat(positions.get(0)).isEqualTo(1L);
        assertThat(positions.get(1)).isEqualTo(2L);
        assertThat(positions.get(2)).isEqualTo(3L);
    }
```

- [ ] **Step 2: Add test — global_position exposed on load**

```java
    @Test
    void loadEventsReturnsGlobalPosition() {
        // given
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());
        eventStore.appendEvents(
                aggregateType, aggregateId, 0,
                List.of(new CreditAccountOpened(creditAccountId, Instant.now())),
                Map.of());

        // when
        List<EventEnvelope> envelopes = eventStore.loadEvents(aggregateType, aggregateId);

        // then
        assertThat(envelopes).hasSize(1);
        assertThat(envelopes.getFirst().globalPosition()).isGreaterThan(0L);
    }
```

- [ ] **Step 3: Add test — loadEventsAfter returns correct slice**

```java
    @Test
    void loadEventsAfterReturnsCorrectSlice() {
        // given — append 5 events
        var aggregateType = "CreditAccount";
        var aggregateId = UUID.randomUUID().toString();
        var creditAccountId = CreditAccountId.of(UUID.randomUUID());
        var now = Instant.now();

        eventStore.appendEvents(
                aggregateType, aggregateId, 0,
                List.of(new CreditAccountOpened(creditAccountId, now)),
                Map.of()); // pos 1
        eventStore.appendEvents(
                aggregateType, aggregateId, 1,
                List.of(new CreditAccountOpened(creditAccountId, now)),
                Map.of()); // pos 2
        eventStore.appendEvents(
                aggregateType, aggregateId, 2,
                List.of(new CreditAccountOpened(creditAccountId, now)),
                Map.of()); // pos 3
        eventStore.appendEvents(
                aggregateType, aggregateId, 3,
                List.of(new CreditAccountOpened(creditAccountId, now)),
                Map.of()); // pos 4
        eventStore.appendEvents(
                aggregateType, aggregateId, 4,
                List.of(new CreditAccountOpened(creditAccountId, now)),
                Map.of()); // pos 5

        // when — load events after position 2 with limit 2
        List<EventEnvelope> envelopes = eventStore.loadEventsAfter(2L, 2);

        // then — should return events at positions 3 and 4
        assertThat(envelopes).hasSize(2);
        assertThat(envelopes.get(0).globalPosition()).isEqualTo(3L);
        assertThat(envelopes.get(1).globalPosition()).isEqualTo(4L);
    }
```

- [ ] **Step 4: Run new tests to verify they pass**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapterIT"`
Expected: BUILD SUCCESSFUL, all 8 tests pass (5 existing + 3 new)

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java
git commit -m "test: add global_position integration tests for EventStore"
```

---

### Task 6: Full Quality Gate

**Files:**

- *(none modified — verification only)*

- [ ] **Step 1: Run full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL — all tests pass, checkstyle 0 errors/warnings, PMD clean, SpotBugs clean, ErrorProne clean, PiTest ≥80% mutation coverage

- [ ] **Step 2: Address any failures**

If `./gradlew check` fails:

- Fix code issues
- Re-run until clean

- [ ] **Step 3: Final commit (if fixes were needed)**

```bash
git add -u
git commit -m "fix: resolve quality gate issues for global_position feature"
```
