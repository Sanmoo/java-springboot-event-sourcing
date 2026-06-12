# Fix qualityTest Architecture Violations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `./gradlew qualityTest` pass by removing the architectural rule violations reported by ArchUnit.

**Architecture:** Preserve the hexagonal boundary: inbound adapters call use cases and DTOs only; core depends only on domain/core abstractions; Spring-specific transaction/configuration concerns stay in adapters or configuration. Do not weaken ArchUnit rules unless a violation is demonstrably a rule bug.

**Tech Stack:** Java, Spring Boot, Gradle, JUnit 5, ArchUnit, Mockito.

---

## Investigation Summary

Command reproduced:

```bash
./gradlew qualityTest
```

Result: 16 quality tests, 4 failures.

Root causes:

1. `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java` depends on `ProjectionProperties` in package `..projection..` and on Spring `TransactionTemplate`. This violates `core_must_not_depend_on_adapters` because core should not know external configuration classes or concrete transaction infrastructure.
2. `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/scheduler/OutboxProjectionWorkerRunner.java` uses Lombok `@Slf4j`, producing direct `org.slf4j.*` dependencies. The inbound adapter rule only allows `org.springframework..` and project packages.
3. `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java` maps `core.port.model.CreditAccountSummaryPage` and `CreditAccountSummary` directly. Controllers are only allowed to depend on use cases/usecase DTOs, not ports.

## File Structure

- Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java`
  - Remove direct `ProjectionProperties` dependency.
  - Remove direct `TransactionTemplate` dependency.
  - Accept batch size as an argument or use a core-owned abstraction.
- Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/TransactionRunner.java`
  - Core port for running a unit of work transactionally.
- Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/transaction/SpringTransactionRunner.java`
  - Adapter implementation backed by `TransactionTemplate`.
- Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/scheduler/OutboxProjectionWorkerRunner.java`
  - Read batch size from `ProjectionProperties` in the scheduler adapter.
  - Call `ProjectionWorker.processOnce(batchSize)`.
  - Replace `@Slf4j` with Spring logging (`org.springframework.core.log.LogAccessor`) or remove logging.
- Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ListCreditAccountsOutput.java`
  - Return usecase DTO data, not `core.port.model` data.
- Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCase.java`
  - Map `CreditAccountSummaryPage` to `ListCreditAccountsOutput` using `CreditAccountOutput` / `PurchaseAuthorizationOutput`.
- Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`
  - Remove `CreditAccountSummary` import and `summaryToMap` method.
  - Map list output using existing `toMap(CreditAccountOutput)`.
- Modify tests:
  - `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java`
  - `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCaseTest.java`
  - Controller/list integration tests only if API response changes unexpectedly; API JSON should remain unchanged.

---

### Task 1: Isolate transaction infrastructure from core projection worker

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/TransactionRunner.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/transaction/SpringTransactionRunner.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java`

- [ ] **Step 1: Add the core transaction port**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/TransactionRunner.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.function.Supplier;

public interface TransactionRunner {
    <T> T runInTransaction(Supplier<T> action);
}
```

- [ ] **Step 2: Add the Spring adapter implementation**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/transaction/SpringTransactionRunner.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.out.transaction;

import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    @Override
    public <T> T runInTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
```

- [ ] **Step 3: Refactor `ProjectionWorker` constructor dependencies**

In `ProjectionWorker`, replace imports and fields:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
```

Remove:

```java
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import org.springframework.transaction.support.TransactionTemplate;
```

Replace fields:

```java
private final ProjectionProperties properties;
private final TransactionTemplate transactionTemplate;
```

with:

```java
private final TransactionRunner transactionRunner;
```

- [ ] **Step 4: Make batch size an input to `processOnce`**

Replace:

```java
public int processOnce() {
    List<OutboxEvent> pending = outbox.findPending(properties.getBatchSize());
```

with:

```java
public int processOnce(int batchSize) {
    List<OutboxEvent> pending = outbox.findPending(batchSize);
```

Replace:

```java
transactionTemplate.execute(status -> {
    processOne(event);
    return null;
});
```

with:

```java
transactionRunner.runInTransaction(() -> {
    processOne(event);
    return null;
});
```

- [ ] **Step 5: Update `ProjectionWorkerTest`**

Remove `ProjectionProperties` and `TransactionTemplate` imports and setup. Use a fake `TransactionRunner`:

```java
TransactionRunner transactionRunner = action -> action.get();
ProjectionWorker worker = new ProjectionWorker(outbox, summaries, projector, transactionRunner);
int processed = worker.processOnce(10);
```

Apply in both tests.

- [ ] **Step 6: Run focused tests**

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorkerTest
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Move projection configuration usage to scheduler adapter and remove disallowed SLF4J dependency

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/scheduler/OutboxProjectionWorkerRunner.java`

- [ ] **Step 1: Inject projection properties into the scheduler**

Add import:

```java
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import org.springframework.core.log.LogAccessor;
```

Remove import:

```java
import lombok.extern.slf4j.Slf4j;
```

Remove annotation:

```java
@Slf4j
```

Add fields:

```java
private final ProjectionWorker worker;
private final ProjectionProperties properties;
private final LogAccessor log = new LogAccessor(OutboxProjectionWorkerRunner.class);
```

- [ ] **Step 2: Pass batch size into the worker**

Replace:

```java
int processed = worker.processOnce();
```

with:

```java
int processed = worker.processOnce(properties.getBatchSize());
```

- [ ] **Step 3: Keep existing logging behavior through Spring logging**

The existing statements can remain as:

```java
log.debug("Projection worker processed " + processed + " events");
log.error(e, "Projection worker tick failed");
```

Use exact `LogAccessor` method signatures supported by the project dependency if compilation indicates a different overload.

- [ ] **Step 4: Run scheduler-related compilation check**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Stop exposing port models through list use case output

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ListCreditAccountsOutput.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCase.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCaseTest.java`

- [ ] **Step 1: Replace `ListCreditAccountsOutput` with usecase DTO fields**

Replace file content with:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;

import java.util.List;

public record ListCreditAccountsOutput(
        List<CreditAccountOutput> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
```

- [ ] **Step 2: Map port model to usecase DTO in `ListCreditAccountsUseCase`**

Add imports:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import java.util.List;
```

Replace:

```java
CreditAccountSummaryPage page = summaries.findAll(new CreditAccountSummaryPageRequest(input.page(), input.size()));
return new ListCreditAccountsOutput(page);
```

with:

```java
CreditAccountSummaryPage page = summaries.findAll(new CreditAccountSummaryPageRequest(input.page(), input.size()));
List<CreditAccountOutput> items = page.items().stream()
        .map(this::toOutput)
        .toList();
return new ListCreditAccountsOutput(items, page.page(), page.size(), page.totalItems(), page.totalPages());
```

Add method:

```java
private CreditAccountOutput toOutput(CreditAccountSummary s) {
    List<PurchaseAuthorizationOutput> auths = s.authorizations().stream()
            .map(a -> new PurchaseAuthorizationOutput(a.authorizationId().toString(), a.amount(), a.status(), a.merchantName()))
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
```

- [ ] **Step 3: Update `ListCreditAccountsUseCaseTest` assertions**

Change assertions that read `output.page().items()` to `output.items()`, and `output.page().page()` to `output.page()`, etc. Preserve repository verification for `CreditAccountSummaryPageRequest`.

- [ ] **Step 4: Run focused tests**

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.core.usecase.ListCreditAccountsUseCaseTest
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Stop REST controller from depending on core ports

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`
- Optionally modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerListIT.java` only if existing JSON expectations are tied to implementation details.

- [ ] **Step 1: Remove port model import**

Remove:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
```

- [ ] **Step 2: Update list response mapping**

Replace:

```java
var items = output.page().items().stream()
        .map(this::summaryToMap)
        .toList();
var response = new PageResponse(
        items,
        output.page().page(),
        output.page().size(),
        output.page().totalItems(),
        output.page().totalPages()
);
```

with:

```java
var items = output.items().stream()
        .map(this::toMap)
        .toList();
var response = new PageResponse(
        items,
        output.page(),
        output.size(),
        output.totalItems(),
        output.totalPages()
);
```

- [ ] **Step 3: Delete `summaryToMap`**

Remove the entire method:

```java
private Map<String, Object> summaryToMap(CreditAccountSummary summary) {
    ...
}
```

The existing `toMap(CreditAccountOutput output)` method should handle all account response mapping.

- [ ] **Step 4: Run controller list tests**

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.CreditAccountControllerListIT
```

Expected: `BUILD SUCCESSFUL` and unchanged response JSON shape.

---

### Task 5: Verify architecture and full regression suite

**Files:**
- No source changes unless verification exposes new root causes.

- [ ] **Step 1: Run quality tests**

```bash
./gradlew qualityTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run unit/integration tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: If available, run acceptance tests**

```bash
./gradlew acceptanceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Inspect final diff**

```bash
git diff -- src/main/java src/test/java src/qualityTest/java
```

Expected: only boundary-preserving refactors described above; no ArchUnit rule weakening.

---

## Self-Review

- Spec coverage: all 4 quality failures map to tasks 1-4.
- Placeholder scan: no intentional TBD/TODO work remains; any overload uncertainty is constrained to `LogAccessor` compilation check.
- Type consistency: REST controller depends on `ListCreditAccountsOutput` and `CreditAccountOutput`; core projection worker depends on `TransactionRunner`, not Spring `TransactionTemplate`.
