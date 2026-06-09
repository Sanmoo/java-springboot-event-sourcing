# Versioned Event Types V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist explicit `.v1` event type names instead of Java class names in the event store.

**Architecture:** Keep event type mapping centralized in `EventTypeMapper`. Change the stored type strings to stable, versioned domain event names while preserving the existing payload format and deserialization flow. Do not add upcasters, legacy aliases, or new event versions yet.

**Tech Stack:** Java 25, Spring Boot, JDBC, PostgreSQL, Jackson, JUnit 5, AssertJ, Gradle.

---

## File Structure

- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/EventTypeMapper.java`
  - Responsibility: map between persisted event type names and `CreditAccountEvent` classes; serialize/deserialize event payloads and metadata.
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java`
  - Responsibility: integration-test event store persistence/loading behavior against PostgreSQL.

---

### Task 1: Assert versioned event type persistence

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java`

- [ ] **Step 1: Write the failing integration test**

Add this test method inside `JdbcEventStoreAdapterIT`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapterIT.appendPersistsVersionedEventTypeName
```

Expected: FAIL because the current mapper persists `CreditAccountOpened` instead of `credit-account.opened.v1`.

- [ ] **Step 3: Commit the failing test only**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java
git commit -m "test: assert versioned event type persistence"
```

---

### Task 2: Persist v1 event type names

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/EventTypeMapper.java`

- [ ] **Step 1: Replace Java class-name event types with explicit v1 names**

In `EventTypeMapper`, replace the `TYPE_TO_CLASS` map with:

```java
    private static final Map<String, Class<? extends CreditAccountEvent>> TYPE_TO_CLASS = Map.of(
            "credit-account.opened.v1", CreditAccountOpened.class,
            "credit-account.credit-limit-assigned.v1", CreditLimitAssigned.class,
            "credit-account.credit-limit-changed.v1", CreditLimitChanged.class,
            "purchase.authorized.v1", PurchaseAuthorized.class,
            "purchase.captured.v1", PurchaseCaptured.class,
            "purchase.authorization-released.v1", PurchaseAuthorizationReleased.class,
            "payment.received.v1", PaymentReceived.class
    );
```

Do not add aliases for old names such as `CreditAccountOpened`.

- [ ] **Step 2: Run the focused integration test**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapterIT.appendPersistsVersionedEventTypeName
```

Expected: PASS.

- [ ] **Step 3: Run the full event store integration test class**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapterIT
```

Expected: PASS. This confirms events written with v1 names can still be loaded and deserialized.

- [ ] **Step 4: Commit the mapper change**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/EventTypeMapper.java
git commit -m "feat: persist versioned event type names"
```

---

### Task 3: Verify all tests and quality checks

**Files:**
- No source changes expected.

- [ ] **Step 1: Run the full verification suite**

Run:

```bash
./gradlew check
```

Expected: PASS.

- [ ] **Step 2: Inspect git status**

Run:

```bash
git status --short
```

Expected: no uncommitted changes except files intentionally left by local tooling. If source or test files are uncommitted, inspect and commit them.

---

## Self-Review

- Spec coverage: Task 1 proves the persisted type is `credit-account.opened.v1`; Task 2 changes all current event mappings to explicit `.v1` names; Task 3 verifies no regressions.
- Placeholder scan: no placeholders, deferred work, or vague test instructions remain.
- Type consistency: all referenced classes and paths exist in the current codebase; commands target the existing Gradle/JUnit setup.
