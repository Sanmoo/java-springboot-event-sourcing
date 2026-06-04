# UUID v7 Identifier Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace production UUID v4 generation with UUID v7 behind a minimal `UniqueIdGenerator` port.

**Architecture:** Add a core port that returns `java.util.UUID`, implement it in an outbound Spring adapter using `com.github.f4b6a3:uuid-creator`, and inject the port into production components that create identifiers. Domain value objects remain wrappers around UUIDs and no production code calls `UUID.randomUUID()` for system-generated IDs. UUIDs supplied by API clients are out of scope and remain caller-provided.

**Tech Stack:** Java 25, Spring Boot 4, Gradle Kotlin DSL, JUnit 5, Mockito, AssertJ, ArchUnit, uuid-creator 6.1.1.

---

## File Structure

- Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/UniqueIdGenerator.java`: minimal core port.
- Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/uuid/UuidV7Generator.java`: Spring component implementation backed by `UuidCreator.getTimeOrderedEpoch()`.
- Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/uuid/UuidV7GeneratorTest.java`: proves generated UUIDs are version 7.
- Modify `build.gradle.kts`: add `implementation("com.github.f4b6a3:uuid-creator:6.1.1")`.
- Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java`: inject generator and create account IDs with it.
- Modify `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java`: use deterministic generator fake and assert generated ID is used.
- Keep `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseInput.java`: caller-provided `AuthorizationId` remains in scope for the API contract.
- Keep `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java`: use the supplied authorization ID and do not inject `UniqueIdGenerator`.
- Keep `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/AuthorizePurchaseRequest.java`: require `authorizationId` in the request body.
- Keep `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`: parse `authorizationId` and pass it to `AuthorizePurchaseInput`.
- Keep tests that construct `AuthorizePurchaseInput` or send authorization request JSON expecting caller-provided authorization IDs to be returned unchanged.
- Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java`: inject generator for event IDs.
- Modify `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java`: inject deterministic generator and assert inserted event IDs use it.
- Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/CreditAccountId.java` and `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/AuthorizationId.java`: remove `newId()` and `UUID.randomUUID()` from production classes after test fixtures are updated to use `of(UUID.randomUUID())`.
- Modify `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java`: allow outbound adapters to depend on `com.github.f4b6a3.uuid..`.

---

### Task 1: Add the UUID v7 generator port and adapter

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/UniqueIdGenerator.java`
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/uuid/UuidV7GeneratorTest.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/uuid/UuidV7Generator.java`
- Modify: `build.gradle.kts`
- Modify: `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java`

- [ ] **Step 1: Add the failing adapter test**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/uuid/UuidV7GeneratorTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.out.uuid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void generateReturnsUuidVersionSeven() {
        var generator = new UuidV7Generator();

        var uuid = generator.generate();

        assertThat(uuid.version()).isEqualTo(7);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.sanmoo.eventsourcing.creditaccount.adapter.out.uuid.UuidV7GeneratorTest'
```

Expected: FAIL because `UuidV7Generator` does not exist.

- [ ] **Step 3: Add the port**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/UniqueIdGenerator.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.UUID;

public interface UniqueIdGenerator {
    UUID generate();
}
```

- [ ] **Step 4: Add the dependency and adapter implementation**

In `build.gradle.kts`, add this line inside `dependencies {}` near the other `implementation` dependencies:

```kotlin
implementation("com.github.f4b6a3:uuid-creator:6.1.1")
```

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/uuid/UuidV7Generator.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.out.uuid;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidV7Generator implements UniqueIdGenerator {
    @Override
    public UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
```

- [ ] **Step 5: Update the outbound architecture allowlist**

In `ArchitectureFitnessFunctions`, update the `outbound_adapters_must_not_depend_on_inbound` rule so the allowed packages include the UUID library:

```java
"com.github.f4b6a3.uuid.."
```

The final `resideInAnyPackage(...)` list for outbound adapters should include:

```java
"java..",
"com.sanmoo.eventsourcing.creditaccount.adapter.out..",
"com.sanmoo.eventsourcing.creditaccount.core..",
"com.sanmoo.eventsourcing.creditaccount.domain..",
"org.springframework..",
"tools.jackson..",
"com.github.f4b6a3.uuid.."
```

- [ ] **Step 6: Run the focused test to verify it passes**

Run:

```bash
./gradlew test --tests 'com.sanmoo.eventsourcing.creditaccount.adapter.out.uuid.UuidV7GeneratorTest'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/UniqueIdGenerator.java src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/uuid/UuidV7Generator.java src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/uuid/UuidV7GeneratorTest.java src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java
git commit -m "feat: add uuid v7 identifier generator"
```

---

### Task 2: Use the generator for credit account IDs

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java`

- [ ] **Step 1: Write the failing use case test changes**

In `OpenCreditAccountUseCaseTest`, add these imports:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import java.util.UUID;
```

Add this field:

```java
private UniqueIdGenerator uniqueIdGenerator;
```

Update `setUp()` to create a deterministic generator and pass it to the use case:

```java
uniqueIdGenerator = () -> UUID.fromString("018f5f4b-6a3c-7000-8000-000000000001");
useCase = new OpenCreditAccountUseCase(support, uniqueIdGenerator);
```

Update the first test assertions after `var output = useCase.execute(input);`:

```java
verify(eventStore).appendEvents(
        eq("CreditAccount"),
        eq("018f5f4b-6a3c-7000-8000-000000000001"),
        eq(0L),
        argThat(events -> events.size() == 1),
        anyMap()
);
assertThat(output.account().creditAccountId()).isEqualTo("018f5f4b-6a3c-7000-8000-000000000001");
assertThat(output.replayed()).isFalse();
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.sanmoo.eventsourcing.creditaccount.core.usecase.OpenCreditAccountUseCaseTest'
```

Expected: FAIL because `OpenCreditAccountUseCase` constructor does not accept `UniqueIdGenerator` yet.

- [ ] **Step 3: Implement minimal production change**

Replace `OpenCreditAccountUseCase.java` with:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;
    private final UniqueIdGenerator uniqueIdGenerator;

    public OpenCreditAccountOutput execute(OpenCreditAccountInput input) {
        CreditAccountId creditAccountId = CreditAccountId.of(uniqueIdGenerator.generate());
        return support.executeIdempotent(
                input.idempotencyKey(),
                "OpenCreditAccount",
                creditAccountId,
                input,
                account -> account.open(now()),
                result -> new OpenCreditAccountOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run:

```bash
./gradlew test --tests 'com.sanmoo.eventsourcing.creditaccount.core.usecase.OpenCreditAccountUseCaseTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java
git commit -m "feat: generate credit account ids through port"
```

---

### Task 3: Scope correction — keep authorization IDs caller-provided

**Files:**
- Keep/restore: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseInput.java`
- Keep/restore: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java`
- Keep/restore: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/AuthorizePurchaseRequest.java`
- Keep/restore: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`
- Keep/restore tests for caller-provided authorization IDs.

- [ ] **Step 1: No-op/scope correction**

Do not generate `AuthorizationId` in `AuthorizePurchaseUseCase`. The authorize purchase REST API continues to require `authorizationId` in the request body. The controller parses that UUID, constructs `AuthorizePurchaseInput` with it, and the use case passes it to `account.authorizePurchase(...)` and includes the same value in `AuthorizePurchaseOutput`.

- [ ] **Step 2: Verify focused authorize tests**

Run:

```bash
./gradlew test --tests 'com.sanmoo.eventsourcing.creditaccount.core.usecase.AuthorizePurchaseUseCaseTest' --tests 'com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.CreditAccountControllerIT'
```

Expected: PASS with tests proving the caller-provided authorization ID is returned and used for capture/release flows.

- [ ] **Step 3: Commit only if a previous implementation generated authorization IDs**

If code had already been changed to generate authorization IDs server-side, restore the API contract and commit:

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseInput.java src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/AuthorizePurchaseRequest.java src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerIT.java
git commit -m "fix: keep client-provided authorization ids"
```

### Task 4: Use the generator for event store event IDs

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java`

- [ ] **Step 1: Write failing integration test changes**

In `JdbcEventStoreAdapterIT`, add this import:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
```

Add a test configuration that supplies deterministic UUIDs:

```java
@TestConfiguration
static class DeterministicIdGeneratorConfiguration {
    @Bean
    UniqueIdGenerator uniqueIdGenerator() {
        var ids = new java.util.concurrent.ConcurrentLinkedQueue<UUID>();
        ids.add(UUID.fromString("018f5f4b-6a3c-7000-8000-000000000101"));
        ids.add(UUID.fromString("018f5f4b-6a3c-7000-8000-000000000102"));
        ids.add(UUID.fromString("018f5f4b-6a3c-7000-8000-000000000103"));
        return ids::remove;
    }
}
```

Add this assertion after loading an appended event:

```java
assertThat(loaded.getFirst().eventId()).isEqualTo(UUID.fromString("018f5f4b-6a3c-7000-8000-000000000101"));
```

- [ ] **Step 2: Run the integration test to verify it fails**

Run:

```bash
./gradlew test --tests 'com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapterIT'
```

Expected: FAIL because `JdbcEventStoreAdapter` still uses `UUID.randomUUID()` for `event_id`.

- [ ] **Step 3: Implement generator injection in the adapter**

In `JdbcEventStoreAdapter.java`, add this import:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
```

Add this field next to the other final fields:

```java
private final UniqueIdGenerator uniqueIdGenerator;
```

Replace:

```java
UUID eventId = UUID.randomUUID();
```

with:

```java
UUID eventId = uniqueIdGenerator.generate();
```

- [ ] **Step 4: Run the integration test to verify it passes**

Run:

```bash
./gradlew test --tests 'com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapterIT'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java
git commit -m "feat: generate event ids through port"
```

---

### Task 5: Remove production static random ID helpers and update test fixtures

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/CreditAccountId.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/AuthorizationId.java`
- Modify: every test file that calls `CreditAccountId.newId()` or `AuthorizationId.newId()`.

- [ ] **Step 1: Verify current static helper usages**

Run:

```bash
./gradlew test --tests 'com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest'
```

Expected before edits: PASS, proving the fixture tests are healthy before replacing helper usage.

- [ ] **Step 2: Replace test fixture helper calls**

In tests only, replace:

```java
CreditAccountId.newId()
```

with:

```java
CreditAccountId.of(UUID.randomUUID())
```

Replace:

```java
AuthorizationId.newId()
```

with:

```java
AuthorizationId.of(UUID.randomUUID())
```

Ensure each changed test file imports `java.util.UUID` if it does not already.

- [ ] **Step 3: Remove static random helpers from production value objects**

Replace `CreditAccountId.java` with:

```java
package com.sanmoo.eventsourcing.creditaccount.domain.model;

import java.util.UUID;

public record CreditAccountId(UUID value) {
    public CreditAccountId {
        java.util.Objects.requireNonNull(value, "value must not be null");
    }

    public static CreditAccountId of(UUID value) { return new CreditAccountId(value); }
}
```

Replace `AuthorizationId.java` with:

```java
package com.sanmoo.eventsourcing.creditaccount.domain.model;

import java.util.UUID;

public record AuthorizationId(UUID value) {
    public AuthorizationId {
        java.util.Objects.requireNonNull(value, "value must not be null");
    }

    public static AuthorizationId of(UUID value) { return new AuthorizationId(value); }
}
```

- [ ] **Step 4: Run tests that used the helpers**

Run:

```bash
./gradlew test --tests 'com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest' --tests 'com.sanmoo.eventsourcing.creditaccount.core.usecase.AssignCreditLimitUseCaseTest' --tests 'com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapterIT'
```

Expected: PASS.

- [ ] **Step 5: Confirm no production UUID.randomUUID remains**

Run:

```bash
find src/main/java -name '*.java' -print0 | xargs -0 grep -n 'UUID.randomUUID' || true
```

Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/CreditAccountId.java src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/AuthorizationId.java src/test/java
git commit -m "refactor: remove production random uuid helpers"
```

---

### Task 6: Run final verification

**Files:**
- No code changes unless verification finds failures.

- [ ] **Step 1: Run LSP diagnostics on touched production files**

Run the available LSP diagnostics tool for these files:

```text
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/UniqueIdGenerator.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/uuid/UuidV7Generator.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseInput.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/AuthorizePurchaseRequest.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/CreditAccountId.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/AuthorizationId.java
```

Expected: no errors.

- [ ] **Step 2: Run all tests**

Run:

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run architecture tests**

Run:

```bash
./gradlew qualityTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run production UUID random scan**

Run:

```bash
find src/main/java -name '*.java' -print0 | xargs -0 grep -n 'UUID.randomUUID' || true
```

Expected: no output.

- [ ] **Step 5: Commit verification-only fixes if any were needed**

If verification required fixes, inspect the changed files and commit the exact verification fixes:

```bash
git status --short
git add build.gradle.kts src/main/java src/test/java src/qualityTest/java
git commit -m "fix: satisfy verification for uuid v7 generation"
```

If no fixes were needed, do not create an empty commit.
