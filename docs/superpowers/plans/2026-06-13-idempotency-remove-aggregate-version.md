# Idempotency Aggregate Version Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify HTTP idempotency replay by removing the duplicated `aggregate_version` column from `idempotency_records`, while keeping `aggregateVersion` in the replay/API payload.

**Architecture:** Treat `idempotency_records` as a replay cache keyed by idempotency key + request hash, not as a command audit log. The event store remains the authoritative source of aggregate versions, and the serialized replay payload remains the source of the response's `aggregateVersion`. Remove `verifyReplayVersion` because it only exists to defend the duplicated column.

**Tech Stack:** Java 25, Spring Boot JDBC, Liquibase YAML changelogs, JUnit 5, AssertJ, Mockito, Testcontainers, PIT/PMD/SpotBugs/ArchUnit quality gate.

---

## File Structure

Files modified by this plan:

- `src/main/resources/db/changelog/010-drop-idempotency-aggregate-version.yaml` — new append-only migration dropping the column.
- `src/main/resources/db/changelog/db.changelog-master.yaml` — include the new migration.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRecord.java` — remove `aggregateVersion` field.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRepository.java` — remove `aggregateVersion` argument from `saveResult`.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java` — remove SQL select/insert references to `aggregate_version`.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java` — remove `verifyReplayVersion` call and method; keep `aggregateVersion` inside replay payload.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapterIT.java` — update persistence assertions.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/*UseCaseTest.java` — update mocked `IdempotencyRecord` constructors and `saveResult` verifications.
- Optional: any tests found by `grep` that still mention the removed repository signature or record field.

---

## Task 1: Add Liquibase migration to drop idempotency aggregate_version

**Files:**
- Create: `src/main/resources/db/changelog/010-drop-idempotency-aggregate-version.yaml`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml`

Use append-only migration. Do not rewrite historical migrations `002` or `003`, because production-like Liquibase changelogs should preserve applied history.

- [ ] **Step 1: Create migration file**

Create `src/main/resources/db/changelog/010-drop-idempotency-aggregate-version.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 010-drop-idempotency-aggregate-version
      author: sanmoo
      changes:
        - dropColumn:
            tableName: idempotency_records
            columnName: aggregate_version
```

- [ ] **Step 2: Include migration in master changelog**

In `src/main/resources/db/changelog/db.changelog-master.yaml`, add the new include after `009-backfill-outbox-deliveries.yaml`:

```yaml
  - include:
      file: db/changelog/010-drop-idempotency-aggregate-version.yaml
```

- [ ] **Step 3: Run migration-backed integration smoke test**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcIdempotencyAdapterIT"
```

Expected initially: this may fail until Tasks 2–4 update Java code. The migration should be syntactically valid once the Java code is updated.

- [ ] **Step 4: Commit after Java/tests are updated**

Do not commit this task alone if the code does not compile. Commit together with Task 2 or Task 3 if needed:

```bash
git add src/main/resources/db/changelog/010-drop-idempotency-aggregate-version.yaml src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "db: drop aggregate_version from idempotency records"
```

---

## Task 2: Remove aggregateVersion from idempotency port model and repository contract

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRecord.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRepository.java`

- [ ] **Step 1: Update `IdempotencyRecord`**

Change:

```java
public record IdempotencyRecord(
        String idempotencyKey,
        String commandType,
        String aggregateId,
        String requestHash,
        String responsePayload,
        long aggregateVersion
) {}
```

to:

```java
public record IdempotencyRecord(
        String idempotencyKey,
        String commandType,
        String aggregateId,
        String requestHash,
        String responsePayload
) {}
```

- [ ] **Step 2: Update `IdempotencyRepository.saveResult` signature**

Change:

```java
void saveResult(String idempotencyKey, String commandType, String aggregateId, String requestHash, String responsePayload, long aggregateVersion);
```

to:

```java
void saveResult(String idempotencyKey, String commandType, String aggregateId, String requestHash, String responsePayload);
```

- [ ] **Step 3: Compile to surface call sites**

Run:

```bash
./gradlew compileJava compileTestJava
```

Expected: failures in adapter/usecase/tests that still use the old signature. Fix in Tasks 3–5.

---

## Task 3: Update JDBC idempotency adapter SQL and mapper

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java`

- [ ] **Step 1: Update select SQL**

Change:

```java
SELECT idempotency_key, command_type, aggregate_id, request_hash, response_payload, aggregate_version
```

to:

```java
SELECT idempotency_key, command_type, aggregate_id, request_hash, response_payload
```

- [ ] **Step 2: Update insert SQL**

Change:

```java
INSERT INTO idempotency_records
    (idempotency_key, command_type, aggregate_id, request_hash, response_payload, aggregate_version, created_at)
VALUES (?, ?, ?, ?, ?, ?, NOW())
```

to:

```java
INSERT INTO idempotency_records
    (idempotency_key, command_type, aggregate_id, request_hash, response_payload, created_at)
VALUES (?, ?, ?, ?, ?, NOW())
```

- [ ] **Step 3: Update row mapper**

Change:

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

to:

```java
private final RowMapper<IdempotencyRecord> rowMapper = (rs, rowNum) -> new IdempotencyRecord(
        rs.getString("idempotency_key"),
        rs.getString("command_type"),
        rs.getString("aggregate_id"),
        rs.getString("request_hash"),
        rs.getString("response_payload")
);
```

- [ ] **Step 4: Update `saveResult` implementation**

Change method signature and `jdbcTemplate.update(...)` arguments from six values after SQL to five:

```java
@Override
public void saveResult(String key, String commandType, String aggregateId, String requestHash, String responsePayload) {
    int inserted = jdbcTemplate.update(
            INSERT_RESULT_SQL,
            key,
            commandType,
            aggregateId,
            requestHash,
            responsePayload
    );
    if (inserted != 1) {
        throw new IllegalStateException("Idempotency result was not inserted for key: " + key);
    }
}
```

- [ ] **Step 5: Run adapter integration test after Task 5 updates it**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcIdempotencyAdapterIT"
```

Expected after test updates: `BUILD SUCCESSFUL`.

---

## Task 4: Simplify replay path in CreditAccountUseCaseSupport

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`

- [ ] **Step 1: Remove replay version verification call**

Change:

```java
ExecutionResult result = deserializeReplay(record.responsePayload());
verifyReplayVersion(record, result);
return outputMapper.apply(result);
```

to:

```java
ExecutionResult result = deserializeReplay(record.responsePayload());
return outputMapper.apply(result);
```

- [ ] **Step 2: Remove `verifyReplayVersion` method**

Delete:

```java
private void verifyReplayVersion(IdempotencyRecord record, ExecutionResult result) {
    if (record.aggregateVersion() != result.aggregateVersion()) {
        throw new RuntimeException("Stored idempotency aggregate version does not match replay payload for key: "
                + record.idempotencyKey());
    }
}
```

- [ ] **Step 3: Update saveResult call**

Change:

```java
idempotencyRepository.saveResult(
        idempotencyKey,
        commandType,
        aggregateId,
        requestHash,
        payload,
        result.aggregateVersion()
);
```

to:

```java
idempotencyRepository.saveResult(
        idempotencyKey,
        commandType,
        aggregateId,
        requestHash,
        payload
);
```

- [ ] **Step 4: Confirm payload still contains aggregateVersion**

Do not remove this line from `serializeResponseResult`:

```java
payload.put("aggregateVersion", result.aggregateVersion());
```

That remains the response/replay version source.

---

## Task 5: Update tests for new idempotency contract

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapterIT.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCaseTest.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java`
- Modify any other test surfaced by compile errors.

- [ ] **Step 1: Find all old constructor/signature usages**

Run:

```bash
grep -R "new IdempotencyRecord\|saveResult(.*anyLong\|aggregateVersion()" -n src/test src/main | grep -E "Idempotency|saveResult|aggregateVersion" || true
```

- [ ] **Step 2: Update `JdbcIdempotencyAdapterIT` save calls**

Change calls like:

```java
idempotencyRepository.saveResult(key, commandType, aggregateId, requestHash, responsePayload, 7L);
```

to:

```java
idempotencyRepository.saveResult(key, commandType, aggregateId, requestHash, responsePayload);
```

- [ ] **Step 3: Remove aggregateVersion persistence assertion**

Delete assertions like:

```java
assertThat(loaded.get().aggregateVersion()).isEqualTo(7L);
```

Keep assertions for key, command type, aggregate id, request hash, and response payload.

- [ ] **Step 4: Update use case `IdempotencyRecord` constructors**

Change constructors from six args:

```java
new IdempotencyRecord(
        key,
        commandType,
        aggregateId,
        requestHash,
        responsePayload,
        1L)
```

to five args:

```java
new IdempotencyRecord(
        key,
        commandType,
        aggregateId,
        requestHash,
        responsePayload)
```

- [ ] **Step 5: Update Mockito saveResult verifications**

Change verifications from:

```java
verify(idempotencyRepository).saveResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
```

to:

```java
verify(idempotencyRepository).saveResult(anyString(), anyString(), anyString(), anyString(), anyString());
```

And `never()` variants similarly.

- [ ] **Step 6: Keep payload assertions for aggregateVersion**

Do not remove tests that parse the saved JSON payload and assert:

```java
.containsEntry("aggregateVersion", 2)
```

Those assertions should remain, because the API/replay payload still carries the version.

- [ ] **Step 7: Run targeted tests**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcIdempotencyAdapterIT" \
  --tests "com.sanmoo.eventsourcing.creditaccount.core.usecase.AssignCreditLimitUseCaseTest" \
  --tests "com.sanmoo.eventsourcing.creditaccount.core.usecase.AuthorizePurchaseUseCaseTest" \
  --tests "com.sanmoo.eventsourcing.creditaccount.core.usecase.OpenCreditAccountUseCaseTest"
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 6: Compile, run broader verification, and commit implementation

**Files:**
- All files changed in Tasks 1–5.

- [ ] **Step 1: Compile all source sets**

Run:

```bash
./gradlew compileJava compileTestJava compileAcceptanceTestJava compileQualityTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run all tests**

Run:

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit implementation**

```bash
git add \
  src/main/resources/db/changelog/010-drop-idempotency-aggregate-version.yaml \
  src/main/resources/db/changelog/db.changelog-master.yaml \
  src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRecord.java \
  src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRepository.java \
  src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java \
  src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java \
  src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapterIT.java \
  src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCaseTest.java \
  src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java \
  src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java
git commit -m "refactor(idempotency): remove duplicated aggregate version column"
```

If compile surfaced additional tests/files, include them in the same commit.

---

## Task 7: Final quality gateway

**Files:** none unless verification exposes trivial fixes.

- [ ] **Step 1: Run full check**

Run:

```bash
./gradlew check
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run acceptance tests**

Run:

```bash
./gradlew acceptanceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Confirm no aggregate_version remains in idempotency code**

Run:

```bash
grep -R "aggregate_version\|verifyReplayVersion\|record.aggregateVersion\|saveResult(.*aggregateVersion" -n \
  src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java \
  src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRecord.java \
  src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyRepository.java \
  src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java \
  src/test/java || true
```

Expected: no old idempotency column/signature references. References to `payload.put("aggregateVersion", ...)`, response DTOs, event store, outbox, and projection aggregate versions are expected elsewhere.

- [ ] **Step 4: Confirm clean git state**

Run:

```bash
git status --short
```

Expected: no output.
