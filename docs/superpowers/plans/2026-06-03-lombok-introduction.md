# Lombok Introduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce Lombok to reduce boilerplate across Spring components and safe domain classes without changing behavior or weakening domain invariants.

**Architecture:** Lombok is compile-time only: Gradle provides it as `compileOnly` plus `annotationProcessor`, not as a runtime dependency. Refactors use targeted annotations (`@RequiredArgsConstructor`, `@Getter`, `@Accessors`) and avoid `@Data` or generated setters in the domain.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Gradle Kotlin DSL, Lombok, JUnit 5, Testcontainers, ArchUnit, Error Prone, SpotBugs, PMD.

---

## File Structure

- Modify `build.gradle.kts`: add Lombok dependency coordinates for main and test annotation processing.
- Modify Spring dependency classes:
  - `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`
  - `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/EventTypeMapper.java`
  - `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java`
  - `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java`
  - `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/*UseCase.java`
  - `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`
- Modify safe domain class:
  - `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`
- Tests are primarily existing regression tests. No new behavior is being introduced, so the implementation must prove equivalence by compilation and existing tests.

---

### Task 1: Add Lombok to Gradle

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add Lombok dependencies**

In the `dependencies { ... }` block, add these lines after the Spring starter implementations and before `runtimeOnly("org.postgresql:postgresql")`:

```kotlin
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
```

Expected resulting section:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
    runtimeOnly("org.postgresql:postgresql")
```

- [ ] **Step 2: Verify dependency resolution and compilation**

Run:

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`. If Lombok version resolution fails, use Maven Central metadata to choose the newest Lombok version that supports Java 25, update all four dependency lines consistently, and rerun `./gradlew compileJava`.

- [ ] **Step 3: Commit Gradle setup**

```bash
git add build.gradle.kts
git commit -m "build: add lombok annotation processor"
```

---

### Task 2: Replace Spring constructor injection boilerplate

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/EventTypeMapper.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCase.java`

- [ ] **Step 1: Add `@RequiredArgsConstructor` to each Spring class with final dependencies**

For each listed class, add this import:

```java
import lombok.RequiredArgsConstructor;
```

Add this annotation next to the Spring stereotype annotation:

```java
@RequiredArgsConstructor
```

Example target for `CreditAccountController.java`:

```java
@RestController
@RequestMapping("/credit-accounts")
@RequiredArgsConstructor
public class CreditAccountController {
```

Example target for a service:

```java
@Service
@RequiredArgsConstructor
public class OpenCreditAccountUseCase {
```

Example target for an adapter:

```java
@Component
@RequiredArgsConstructor
public class EventTypeMapper {
```

- [ ] **Step 2: Remove manual constructors that only assign final dependencies**

Delete constructors whose body is only `this.field = field;` assignments for final dependencies.

For `CreditAccountController.java`, remove the full constructor beginning with:

```java
    public CreditAccountController(
            OpenCreditAccountUseCase openCreditAccountUseCase,
```

and ending after:

```java
        this.getCreditAccountUseCase = getCreditAccountUseCase;
    }
```

For `OpenCreditAccountUseCase.java`, remove:

```java
    public OpenCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }
```

Apply the same pattern to the other listed use cases and adapters. Do not remove constructors that contain non-assignment logic.

- [ ] **Step 3: Handle `JdbcEventStoreAdapter` row mapper explicitly**

In `JdbcEventStoreAdapter.java`, after adding `@RequiredArgsConstructor` to the outer class, keep this field:

```java
    private final RowMapper<EventEnvelope> rowMapper;
```

If the current constructor initializes `rowMapper = new EventEnvelopeRowMapper(eventTypeMapper);`, replace the field with inline initialization so Lombok only needs `jdbcTemplate` and `eventTypeMapper`:

```java
    private final RowMapper<EventEnvelope> rowMapper = new EventEnvelopeRowMapper();
```

Then change the inner class to use the outer `eventTypeMapper` field and remove its constructor and inner field:

```java
    private class EventEnvelopeRowMapper implements RowMapper<EventEnvelope> {

        @Override
        public EventEnvelope mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID eventId = rs.getObject("event_id", UUID.class);
            String aggregateType = rs.getString("aggregate_type");
            String aggregateId = rs.getString("aggregate_id");
            long aggregateVersion = rs.getLong("aggregate_version");
            String eventType = rs.getString("event_type");
            String payload = rs.getString("payload");
            String metadataJson = rs.getString("metadata");
            Timestamp occurredAtTs = rs.getTimestamp("occurred_at");
            Instant occurredAt = occurredAtTs.toInstant();

            CreditAccountEvent event = eventTypeMapper.deserialize(eventType, payload);
            Map<String, String> metadata = deserializeMetadata(metadataJson);

            return new EventEnvelope(eventId, aggregateType, aggregateId, aggregateVersion, event, occurredAt, metadata);
        }

        private Map<String, String> deserializeMetadata(String metadataJson) {
            if (metadataJson == null || metadataJson.isBlank()) {
                return Map.of();
            }
            return eventTypeMapper.deserializeMetadata(metadataJson);
        }
    }
```

This preserves the same row mapping behavior without introducing an extra required constructor parameter for `rowMapper`.

- [ ] **Step 4: Compile main sources**

Run:

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`. If Checkstyle flags import order later, reorder imports to match the existing style: project imports, Java imports, then framework/tool imports as already used in each file.

- [ ] **Step 5: Commit Spring constructor refactor**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/EventTypeMapper.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase
git commit -m "refactor: use lombok for spring constructors"
```

---

### Task 3: Apply Lombok safely in the domain aggregate

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`

- [ ] **Step 1: Replace the private aggregate constructor with Lombok**

Add these imports to `CreditAccount.java`:

```java
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
```

Annotate the class like this:

```java
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CreditAccount {
```

Remove this manual constructor:

```java
    private CreditAccount(CreditAccountId id) { this.id = id; }
```

This keeps creation private and preserves the existing `CreditAccount.rehydrate(...)` factory path.

- [ ] **Step 2: Replace the trivial `version()` getter with Lombok without changing API**

Annotate the `version` field:

```java
    @Getter
    @Accessors(fluent = true)
    private long version;
```

Remove the manual method:

```java
    public long version() { return version; }
```

Do not add getters or setters for mutable aggregate internals such as `opened`, `creditLimit`, `outstandingBalance`, `authorizedAmount`, or `authorizations`.

- [ ] **Step 3: Compile main sources**

Run:

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`. Any failure mentioning `account.version()` means the Lombok fluent accessor was not generated; verify both `@Getter` and `@Accessors(fluent = true)` are on the `version` field.

- [ ] **Step 4: Commit domain Lombok refactor**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java
git commit -m "refactor: use lombok in credit account aggregate"
```

---

### Task 4: Run regression checks and static quality gates

**Files:**
- No source changes expected unless checks reveal import/style issues.

- [ ] **Step 1: Run unit and integration tests**

Run:

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`. If Testcontainers fails because Docker is unavailable, record the exact failure and still run `./gradlew compileJava compileTestJava`.

- [ ] **Step 2: Run architecture tests**

Run:

```bash
./gradlew qualityTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run full check if environment can support PIT/Testcontainers runtime**

Run:

```bash
./gradlew check
```

Expected: `BUILD SUCCESSFUL`. If this is too slow or blocked by Docker/PIT runtime constraints, stop it cleanly and report the limitation with the successful results from `compileJava`, `test` or `compileTestJava`, and `qualityTest`.

- [ ] **Step 4: Fix import or static-analysis issues if any**

If Checkstyle fails only because of import order, make imports consistent in the failing file. Example order used in this project varies by file, so prefer minimal changes and remove unused imports.

Then rerun the failing command exactly. Expected: the previously failing command succeeds or the remaining failure is environmental and documented.

- [ ] **Step 5: Commit verification fixes, if any**

Only if Step 4 changed files:

```bash
git add src/main/java build.gradle.kts
git commit -m "chore: satisfy quality checks after lombok refactor"
```

---

## Self-Review

- Spec coverage: Gradle setup is covered by Task 1; Spring constructors by Task 2; safe domain usage by Task 3; validation and quality gates by Task 4.
- Placeholder scan: no `TBD`, vague future implementation, or missing command remains.
- Type consistency: `CreditAccount.version()` remains available through `@Getter` plus `@Accessors(fluent = true)`; Spring constructor injection remains constructor-based via `@RequiredArgsConstructor`; `JdbcEventStoreAdapter` avoids accidentally adding `RowMapper<EventEnvelope>` to its generated constructor.
