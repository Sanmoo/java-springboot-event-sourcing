# Idempotency Transactional Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace durable `STARTED` idempotency records with transaction-scoped PostgreSQL advisory locking and completed-only replay records.

**Architecture:** `CreditAccountUseCaseSupport` becomes the transactional orchestration boundary for idempotent command execution. `IdempotencyPort` becomes a result-oriented port with lock, lookup, and save operations. `JdbcIdempotencyAdapter` uses `pg_advisory_xact_lock` and stores only completed replay payloads.

**Tech Stack:** Java 25, Spring Boot 4, Spring JDBC, Spring transactions, PostgreSQL 18 advisory transaction locks, Liquibase, JUnit 5, Mockito, AssertJ, Testcontainers.

---

## File Structure

Modify these files:

- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyPort.java` — replace `start/complete` with `lockKey/findByKey/saveResult`.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRecord.java` — new core record representing a completed replayable idempotency result.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyDecision.java` — delete after callers no longer use it.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java` — add `@Transactional`, use the new port, append idempotency metadata, save replay result in the same transaction.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java` — implement advisory lock, completed-result lookup, and completed-result insert.
- `src/main/resources/db/changelog/003-refine-idempotency-records.yaml` — migrate idempotency table to completed-only shape.
- `src/main/resources/db/changelog/db.changelog-master.yaml` — include the new changeset.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/*UseCaseTest.java` — update mocks to the new port and add focused assertions for replay/conflict/metadata/saveResult.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapterIT.java` — replace old STARTED/COMPLETED tests with completed-result and advisory-lock tests.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerIT.java` — keep existing replay behavior test and add one assertion that no durable `STARTED` status column remains.

Do not modify domain events or aggregate behavior.

---

## Task 1: Add completed-only idempotency port model

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRecord.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyPort.java`
- Delete in Step 12: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyDecision.java`
- Test: compile-focused only in this task; use case tests are updated in Task 2.

- [ ] **Step 1: Replace the port contract**

Edit `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyPort.java` to exactly:

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.Optional;

public interface IdempotencyPort {
    void lockKey(String key);

    Optional<IdempotencyRecord> findByKey(String key);

    void saveResult(
            String key,
            String commandType,
            String aggregateId,
            String requestHash,
            String responsePayload,
            long aggregateVersion
    );
}
```

- [ ] **Step 2: Add the completed-result record**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRecord.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

public record IdempotencyRecord(
        String idempotencyKey,
        String commandType,
        String aggregateId,
        String requestHash,
        String responsePayload,
        long aggregateVersion
) {}
```

- [ ] **Step 3: Do not delete `IdempotencyDecision` yet**

Leave `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyDecision.java` in place until Task 2 updates all imports. Deleting it now creates noisy compile failures before the use case tests are rewritten.

- [ ] **Step 4: Run compile and confirm expected failures are limited to old callers**

Run:

```bash
./gradlew compileJava testClasses --continue
```

Expected: FAIL. Failures should reference old methods such as `idempotencyPort.start(...)`, `idempotencyPort.complete(...)`, or old `IdempotencyDecision` usage. There should be no syntax error in the new `IdempotencyPort` or `IdempotencyRecord` files.

- [ ] **Step 5: Commit**

Do not commit this task by itself if the project cannot compile. Continue to Task 2, then commit Tasks 1 and 2 together after focused unit tests pass.

---

## Task 2: Refactor use case orchestration to transaction-scoped replay records

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`
- Modify tests:
  - `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java`
  - `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCaseTest.java`
  - `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java`
  - `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCaseTest.java`
  - `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCaseTest.java`
  - `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCaseTest.java`
  - `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCaseTest.java`
- Delete after successful compile: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyDecision.java`

- [ ] **Step 1: Update imports in `CreditAccountUseCaseSupport`**

Remove:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
```

Add:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
```

Keep existing imports for `Map`, `List`, and `Function`.

- [ ] **Step 2: Add `@Transactional` to `executeIdempotent`**

Change the method declaration in `CreditAccountUseCaseSupport` to:

```java
    @Transactional
    public <I, O> O executeIdempotent(
            String idempotencyKey,
            String commandType,
            CreditAccountId creditAccountId,
            I input,
            CommandExecutor executor,
            Function<ExecutionResult, O> outputMapper
    ) {
```

This method is called from other Spring services through the injected `CreditAccountUseCaseSupport`, so Spring proxy transaction interception applies.

- [ ] **Step 3: Replace the old decision switch**

Replace the body of `executeIdempotent` after `requestHash` calculation with:

```java
        idempotencyPort.lockKey(idempotencyKey);

        Optional<IdempotencyRecord> existing = idempotencyPort.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!requestHash.equals(record.requestHash())) {
                throw new IdempotencyConflictException("idempotency key reused with different request hash");
            }
            ExecutionResult result = deserializeReplay(record.responsePayload());
            verifyReplayVersion(record, result);
            return outputMapper.apply(result);
        }

        ExecutionResult result = execute(aggregateId, creditAccountId, executor, idempotencyKey, commandType, requestHash);
        String payload = serializeResult(result);
        idempotencyPort.saveResult(
                idempotencyKey,
                commandType,
                aggregateId,
                requestHash,
                payload,
                result.aggregateVersion()
        );
        return outputMapper.apply(result);
```

- [ ] **Step 4: Add a tiny replay consistency helper**

Add this private method near `deserializeReplay`:

```java
    private void verifyReplayVersion(IdempotencyRecord record, ExecutionResult result) {
        if (record.aggregateVersion() != result.aggregateVersion()) {
            throw new RuntimeException("Stored idempotency aggregate version does not match replay payload for key: "
                    + record.idempotencyKey());
        }
    }
```

This keeps `aggregate_version` meaningful and detects corrupted replay rows.

- [ ] **Step 5: Change `execute` to accept idempotency metadata**

Change the private `execute` signature to:

```java
    private ExecutionResult execute(
            String aggregateId,
            CreditAccountId creditAccountId,
            CommandExecutor executor,
            String idempotencyKey,
            String commandType,
            String requestHash
    ) {
```

Inside that method, replace the current append call:

```java
        AppendResult appendResult = eventStore.appendEvents(
                AGGREGATE_TYPE, aggregateId, expectedVersion, newEvents, Map.of());
```

with:

```java
        Map<String, String> metadata = Map.of(
                "idempotencyKey", idempotencyKey,
                "commandType", commandType,
                "requestHash", requestHash
        );
        AppendResult appendResult = eventStore.appendEvents(
                AGGREGATE_TYPE, aggregateId, expectedVersion, newEvents, metadata);
```

- [ ] **Step 6: Change `deserializeReplay` signature**

Replace:

```java
    private ExecutionResult deserializeReplay(IdempotencyDecision.Replay replay) {
```

with:

```java
    private ExecutionResult deserializeReplay(String responsePayload) {
```

Inside the method, replace:

```java
            Map<String, Object> raw = objectMapper.readValue(replay.responsePayload(), objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
```

with:

```java
            Map<String, Object> raw = objectMapper.readValue(responsePayload, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
```

- [ ] **Step 7: Update happy-path mocks in every core use case test**

In each core use case test that currently has:

```java
when(idempotencyPort.start(any(), eq("SomeCommand"), any(), any()))
        .thenReturn(new IdempotencyDecision.Started("key-1"));
```

replace it with:

```java
doNothing().when(idempotencyPort).lockKey(anyString());
when(idempotencyPort.findByKey(anyString())).thenReturn(java.util.Optional.empty());
```

Remove the `IdempotencyDecision` import from each test and add `java.util.Optional` only if using `Optional.empty()` without full qualification.

- [ ] **Step 8: Update replay tests to return `IdempotencyRecord`**

For `OpenCreditAccountUseCaseTest.sameIdempotencyKeyAndHashReturnsReplayedOutput`, replace the old stub:

```java
when(idempotencyPort.start(any(), eq("OpenCreditAccount"), any(), any()))
        .thenReturn(new IdempotencyDecision.Replay(previousPayload));
```

with:

```java
doNothing().when(idempotencyPort).lockKey(anyString());
when(idempotencyPort.findByKey(eq("key-2"))).thenReturn(java.util.Optional.of(
        new IdempotencyRecord(
                "key-2",
                "OpenCreditAccount",
                "550e8400-e29b-41d4-a716-446655440000",
                calculateRequestHash(new OpenCreditAccountInput("key-2")),
                previousPayload,
                1L
        )
));
```

Add this helper method to the test class:

```java
    private String calculateRequestHash(Object input) throws Exception {
        byte[] serialized = objectMapper.writeValueAsBytes(input);
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(md.digest(serialized));
    }
```

Add import:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
```

- [ ] **Step 9: Update conflict test in `AssignCreditLimitUseCaseTest`**

Replace the old conflict stub:

```java
when(idempotencyPort.start(eq("conflict-key"), eq("AssignCreditLimit"), eq(creditAccountId.value().toString()), any()))
        .thenReturn(new IdempotencyDecision.Conflict("idempotency key reused with different payload"));
```

with:

```java
doNothing().when(idempotencyPort).lockKey(eq("conflict-key"));
when(idempotencyPort.findByKey(eq("conflict-key"))).thenReturn(java.util.Optional.of(
        new IdempotencyRecord(
                "conflict-key",
                "AssignCreditLimit",
                creditAccountId.value().toString(),
                "different-request-hash",
                "{\"aggregateId\":\"%s\",\"aggregateVersion\":1,\"responseData\":{}}".formatted(creditAccountId.value()),
                1L
        )
));
```

Change the final verification from:

```java
verify(idempotencyPort, never()).complete(anyString(), anyString());
```

to:

```java
verify(idempotencyPort, never()).saveResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
```

Add import:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
```

- [ ] **Step 10: Update serialized result assertion in `AssignCreditLimitUseCaseTest`**

Replace:

```java
ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
verify(idempotencyPort).complete(eq("serialize-key"), payloadCaptor.capture());
```

with:

```java
ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
verify(idempotencyPort).saveResult(
        eq("serialize-key"),
        eq("AssignCreditLimit"),
        eq(accountId.toString()),
        anyString(),
        payloadCaptor.capture(),
        eq(2L)
);
```

- [ ] **Step 11: Add metadata assertion to one focused core test**

In `AssignCreditLimitUseCaseTest.successfulCommandCompletesIdempotencyWithSerializedResult`, add an `ArgumentCaptor<Map<String, String>>` for append metadata:

```java
@SuppressWarnings("unchecked")
ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
verify(eventStore).appendEvents(
        eq("CreditAccount"),
        eq(accountId.toString()),
        eq(1L),
        anyList(),
        metadataCaptor.capture()
);
assertThat(metadataCaptor.getValue())
        .containsKeys("idempotencyKey", "commandType", "requestHash")
        .containsEntry("idempotencyKey", "serialize-key")
        .containsEntry("commandType", "AssignCreditLimit");
```

- [ ] **Step 12: Delete old decision type**

After all imports are gone, delete:

```bash
rm src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyDecision.java
```

- [ ] **Step 13: Run focused unit tests**

Run:

```bash
./gradlew test --tests '*UseCaseTest'
```

Expected: PASS.

- [ ] **Step 14: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase
git commit -m "refactor: make idempotency replay result-oriented"
```

---

## Task 3: Migrate idempotency table to completed-only records

**Files:**
- Create: `src/main/resources/db/changelog/003-refine-idempotency-records.yaml`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml`
- Test: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapterIT.java` will validate through Testcontainers in Task 4.

- [ ] **Step 1: Add Liquibase changeset**

Create `src/main/resources/db/changelog/003-refine-idempotency-records.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 003-refine-idempotency-records
      author: sanmoo
      changes:
        - delete:
            tableName: idempotency_records
            where: response_payload IS NULL
        - addColumn:
            tableName: idempotency_records
            columns:
              - column:
                  name: aggregate_version
                  type: BIGINT
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
        - dropDefaultValue:
            tableName: idempotency_records
            columnName: aggregate_version
            columnDataType: BIGINT
        - addNotNullConstraint:
            tableName: idempotency_records
            columnName: response_payload
            columnDataType: TEXT
        - dropColumn:
            tableName: idempotency_records
            columnName: status
        - dropColumn:
            tableName: idempotency_records
            columnName: completed_at
```

- [ ] **Step 2: Include changeset in master changelog**

Open `src/main/resources/db/changelog/db.changelog-master.yaml` and add this include after `002-create-idempotency-records.yaml`:

```yaml
  - include:
      file: db/changelog/003-refine-idempotency-records.yaml
```

The final file should include all three changelogs in order.

- [ ] **Step 3: Run migration through a focused Spring context test**

Run:

```bash
./gradlew test --tests JdbcIdempotencyAdapterIT
```

Expected: FAIL for adapter API mismatch until Task 4 updates `JdbcIdempotencyAdapterIT`. There should be no Liquibase YAML parsing error. If there is a Liquibase error, fix this task before moving on.

- [ ] **Step 4: Commit**

Do not commit this task separately if `JdbcIdempotencyAdapterIT` cannot compile because of the old API. Continue to Task 4 and commit Tasks 3 and 4 together.

---

## Task 4: Implement PostgreSQL advisory lock idempotency adapter

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapterIT.java`

- [ ] **Step 1: Rewrite adapter SQL constants and imports**

In `JdbcIdempotencyAdapter`, remove imports for `IdempotencyDecision` and `DuplicateKeyException`. Add:

```java
import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.springframework.dao.CannotAcquireLockException;
```

Use these SQL constants:

```java
    private static final String SET_LOCK_TIMEOUT_SQL = "SET LOCAL lock_timeout = '5s'";

    private static final String ADVISORY_LOCK_SQL = "SELECT pg_advisory_xact_lock(?)";

    private static final String SELECT_BY_KEY_SQL = """
            SELECT idempotency_key, command_type, aggregate_id, request_hash, response_payload, aggregate_version
            FROM idempotency_records
            WHERE idempotency_key = ?
            """;

    private static final String INSERT_RESULT_SQL = """
            INSERT INTO idempotency_records
                (idempotency_key, command_type, aggregate_id, request_hash, response_payload, aggregate_version, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """;
```

- [ ] **Step 2: Replace row mapper**

Replace the current private `IdempotencyRecord` inner record and mapper with:

```java
    private final RowMapper<IdempotencyRecord> rowMapper = (rs, rowNum) -> new IdempotencyRecord(
            rs.getString("idempotency_key"),
            rs.getString("command_type"),
            rs.getString("aggregate_id"),
            rs.getString("request_hash"),
            rs.getString("response_payload"),
            rs.getLong("aggregate_version")
    );
```

Delete the private inner record at the bottom of the class.

- [ ] **Step 3: Implement `lockKey`**

Add:

```java
    @Override
    public void lockKey(String key) {
        long lockId = lockId(key);
        try {
            jdbcTemplate.execute(SET_LOCK_TIMEOUT_SQL);
            jdbcTemplate.query(ADVISORY_LOCK_SQL, rs -> null, lockId);
        } catch (CannotAcquireLockException e) {
            throw new IdempotencyConflictException("idempotency key is currently being processed");
        }
    }
```

- [ ] **Step 4: Implement lookup and save**

Add:

```java
    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        return jdbcTemplate.query(SELECT_BY_KEY_SQL, rowMapper, key).stream().findFirst();
    }

    @Override
    public void saveResult(
            String key,
            String commandType,
            String aggregateId,
            String requestHash,
            String responsePayload,
            long aggregateVersion
    ) {
        int inserted = jdbcTemplate.update(
                INSERT_RESULT_SQL,
                key,
                commandType,
                aggregateId,
                requestHash,
                responsePayload,
                aggregateVersion
        );
        if (inserted != 1) {
            throw new IllegalStateException("Idempotency result was not inserted for key: " + key);
        }
    }
```

- [ ] **Step 5: Add deterministic lock-id hashing**

Add these private methods:

```java
    private long lockId(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("credit-account-idempotency:" + key).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
```

- [ ] **Step 6: Replace `JdbcIdempotencyAdapterIT` imports**

Remove:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
```

Add:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
```

Autowire:

```java
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;
```

- [ ] **Step 7: Add cleanup before each integration test**

Add:

```java
    @org.junit.jupiter.api.BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM idempotency_records");
    }
```

- [ ] **Step 8: Replace first adapter test with missing-key lookup**

Replace `firstKeyHashesToStarted` with:

```java
    @Test
    void missingKeyReturnsEmptyResult() {
        Optional<IdempotencyRecord> result = idempotencyPort.findByKey(UUID.randomUUID().toString());

        assertThat(result).isEmpty();
    }
```

- [ ] **Step 9: Replace replay test with save/find test**

Replace `repeatedKeyWithSameHashReturnsReplay` with:

```java
    @Test
    void saveResultPersistsCompletedReplayRecord() {
        var key = UUID.randomUUID().toString();
        var commandType = "CreateAccount";
        var aggregateId = UUID.randomUUID().toString();
        var requestHash = "hash-456";
        var responsePayload = "{\"status\":\"ok\"}";

        idempotencyPort.saveResult(key, commandType, aggregateId, requestHash, responsePayload, 7L);

        Optional<IdempotencyRecord> loaded = idempotencyPort.findByKey(key);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().idempotencyKey()).isEqualTo(key);
        assertThat(loaded.get().commandType()).isEqualTo(commandType);
        assertThat(loaded.get().aggregateId()).isEqualTo(aggregateId);
        assertThat(loaded.get().requestHash()).isEqualTo(requestHash);
        assertThat(loaded.get().responsePayload()).isEqualTo(responsePayload);
        assertThat(loaded.get().aggregateVersion()).isEqualTo(7L);
    }
```

- [ ] **Step 10: Replace different-hash adapter test with data-only assertion**

Replace `repeatedKeyWithDifferentHashReturnsConflict` with:

```java
    @Test
    void findByKeyExposesStoredHashForCoreConflictDecision() {
        var key = UUID.randomUUID().toString();
        idempotencyPort.saveResult(
                key,
                "CreateAccount",
                UUID.randomUUID().toString(),
                "original-hash",
                "{\"status\":\"ok\"}",
                1L
        );

        Optional<IdempotencyRecord> loaded = idempotencyPort.findByKey(key);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().requestHash()).isEqualTo("original-hash");
    }
```

- [ ] **Step 11: Add same-key advisory lock serialization integration test**

Add this test to `JdbcIdempotencyAdapterIT`:

```java
    @Test
    void lockKeySerializesConcurrentTransactionsForSameKey() throws Exception {
        var key = UUID.randomUUID().toString();
        var firstHasLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondAcquiredLock = new AtomicBoolean(false);
        var executor = Executors.newFixedThreadPool(2);
        var transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            var first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyPort.lockKey(key);
                firstHasLock.countDown();
                try {
                    assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }));

            assertThat(firstHasLock.await(10, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyPort.lockKey(key);
                secondAcquiredLock.set(true);
            }));

            Thread.sleep(250);
            assertThat(secondAcquiredLock).isFalse();

            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(secondAcquiredLock).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
```

- [ ] **Step 12: Add different-key advisory lock integration test**

Add:

```java
    @Test
    void lockKeyAllowsDifferentKeysToProceedConcurrently() throws Exception {
        var firstHasLock = new CountDownLatch(1);
        var secondAcquiredLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            var first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyPort.lockKey("key-a-" + UUID.randomUUID());
                firstHasLock.countDown();
                try {
                    assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }));

            assertThat(firstHasLock.await(10, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyPort.lockKey("key-b-" + UUID.randomUUID());
                secondAcquiredLock.countDown();
            }));

            assertThat(secondAcquiredLock.await(2, TimeUnit.SECONDS)).isTrue();
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
```

- [ ] **Step 13: Run focused adapter integration tests**

Run:

```bash
./gradlew test --tests JdbcIdempotencyAdapterIT
```

Expected: PASS.

- [ ] **Step 14: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java \
        src/main/resources/db/changelog/003-refine-idempotency-records.yaml \
        src/main/resources/db/changelog/db.changelog-master.yaml \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapterIT.java
git commit -m "feat: serialize idempotency with postgres advisory locks"
```

---

## Task 5: Verify atomic event append and idempotency replay through REST/integration behavior

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerIT.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java` for a direct metadata persistence assertion.

- [ ] **Step 1: Keep existing REST replay test and add schema assertion support**

In `CreditAccountControllerIT`, add import:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
```

Add field:

```java
    @Autowired
    private JdbcTemplate jdbcTemplate;
```

- [ ] **Step 2: Add REST assertion that no status column remains**

Add this test:

```java
    @Test
    void idempotencyRecordsDoNotHaveDurableStartedStatusColumn() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_name = 'idempotency_records'
                  AND column_name = 'status'
                """,
                Integer.class
        );

        assertThat(count).isZero();
    }
```

- [ ] **Step 3: Add direct event metadata persistence assertion**

In `JdbcEventStoreAdapterIT.appendThenLoadReturnsSameEventTypeAndData`, change the append call metadata from `Map.of()` to:

```java
        Map<String, String> metadata = Map.of(
                "idempotencyKey", "metadata-key-1",
                "commandType", "OpenCreditAccount",
                "requestHash", "hash-1"
        );
        AppendResult result = eventStorePort.appendEvents(
                aggregateType, aggregateId, 0, List.of(openedEvent), metadata);
```

Then add after `EventEnvelope envelope = envelopes.getFirst();`:

```java
        assertThat(envelope.metadata())
                .containsEntry("idempotencyKey", "metadata-key-1")
                .containsEntry("commandType", "OpenCreditAccount")
                .containsEntry("requestHash", "hash-1");
```

Keep `emptyMetadataIsPersistedAsEmptyJsonObject` unchanged to preserve empty metadata coverage.

- [ ] **Step 4: Run focused integration tests**

Run:

```bash
./gradlew test --tests CreditAccountControllerIT --tests JdbcEventStoreAdapterIT
```

Expected: PASS. The existing `idempotencyKeyReturnsSameResult` test should still pass: first POST returns `201 CREATED`; second POST with same key returns replay with the same body.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerIT.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java
git commit -m "test: verify completed-only idempotency behavior"
```

---

## Task 6: Run full verification and clean up old idempotency symbols

**Files:**
- Inspect all source and test files for stale references.
- Inspect all production changes made by Tasks 1-5.

- [ ] **Step 1: Search for removed API names**

Run:

```bash
grep -R "IdempotencyDecision\|\.start(\|\.complete(\|STARTED\|COMPLETED\|completed_at\|status" -n src/main/java src/test/java src/main/resources/db/changelog README.md docs/superpowers/specs/2026-06-09-idempotency-transactional-replay-design.md
```

Expected remaining matches:

- Design document references explaining the old problem are acceptable.
- Changelog `002-create-idempotency-records.yaml` still contains old columns because it is historical.
- No matches in `src/main/java` except words unrelated to idempotency status.
- No matches in `src/test/java` except the schema test asserting the status column is absent.

- [ ] **Step 2: Run LSP diagnostics on changed Java sources**

Run diagnostics for these files:

```text
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyPort.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRecord.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java
```

Expected: no blocking Java errors.

- [ ] **Step 3: Run full test suite**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 4: Run broader verification if project quality tasks are configured**

Run:

```bash
./gradlew check
```

Expected: PASS. If `check` includes long-running quality gates and fails for unrelated pre-existing warnings, capture the output and fix only issues caused by this change.

- [ ] **Step 5: Check git status**

Run:

```bash
git status --short
```

Expected: either clean working tree or only files intentionally changed by a final fix.

- [ ] **Step 6: Final commit if cleanup changes were needed**

If Task 6 required any edits, commit them:

```bash
git add src/main/java src/test/java src/main/resources/db/changelog
git commit -m "chore: clean up idempotency replay refactor"
```

If Task 6 required no edits, do not create an empty commit.

---

## Acceptance Mapping

- No durable `STARTED`: Tasks 1, 3, 4, 5, 6.
- Replay record stores completed response only: Tasks 1, 2, 3, 4.
- Event append and idempotency result are in one transaction: Task 2.
- Same key concurrent requests are serialized: Task 4.
- Retry after successful commit returns stored replay: Tasks 2, 5.
- Retry after rollback has no partial record: covered by transaction design in Task 2 and absence of durable started rows in Tasks 3-5.
- Events include idempotency metadata: Tasks 2 and 5.
- Existing REST same-key semantics are preserved: Task 5.
