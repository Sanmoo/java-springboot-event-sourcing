# Projection Quality Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Spring transaction infrastructure from `core.projection`, harden the ArchUnit boundary so future leaks are caught automatically, and add enough behavioral tests for the projection pipeline so `./gradlew check` passes (including PIT with `mutationThreshold = 80`).

**Architecture:** Replace direct `PlatformTransactionManager` / `TransactionTemplate` usage in `ProjectionWorker` and `StaleDeliveryRecovery` with the existing `core.port.TransactionRunner` port. Tighten the ArchUnit allow-list for Spring in `core` so any non-allow-listed `org.springframework..` package is denied. Add unit tests driven by the PIT survivors (behavior of the projection pipeline), not by implementation details.

**Tech Stack:** Java 25, Spring Boot 4, JUnit 5, Mockito, AssertJ, ArchUnit 1.4, Pitest 1.25.

**Spec reference:** `docs/superpowers/specs/2026-06-12-projection-quality-gateway-design.md`

---

## File Structure

Files modified by this plan:

- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/StaleDeliveryRecovery.java`
- `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionGatingResultTest.java` (new)
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerResultTest.java` (new)
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/StaleDeliveryRecoveryTest.java` (new)
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java` (extra)
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java` (extra)

The implementation must commit the leftover import fixes (the uncommitted `git status` modifications) as a preliminary commit before the main worktree changes begin.

---

## Task 1: Commit leftover checkstyle import fixes

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java`

These three files already have unused/redundant imports removed from the prior work but are still uncommitted. They must be committed first so the working tree is clean before this plan's changes are applied.

- [ ] **Step 1: Verify `./gradlew checkstyleMain checkstyleTest` passes**

Run: `./gradlew checkstyleMain checkstyleTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Commit the three fixes as a chore commit**

```bash
git add \
  src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java \
  src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java \
  src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java
git commit -m "chore(checkstyle): remove unused and redundant imports"
```

---

## Task 2: ProjectionWorker depends on TransactionRunner

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java`

The current code has `private final PlatformTransactionManager transactionManager;` and uses `new TransactionTemplate(transactionManager).execute(...)`. Replace with the `TransactionRunner` port.

- [ ] **Step 1: Replace import statements**

In `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java`, replace:

```java
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
```

with:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
```

- [ ] **Step 2: Replace the field**

Replace:

```java
    private final PlatformTransactionManager transactionManager;
```

with:

```java
    private final TransactionRunner transactionRunner;
```

- [ ] **Step 3: Replace the claim transaction**

In `processOnce`, replace:

```java
        List<OutboxDelivery> claimed = new TransactionTemplate(transactionManager).execute(status ->
                deliveries.claimPending(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR,
                        properties.getWorkerId(), batchSize));
```

with:

```java
        List<OutboxDelivery> claimed = transactionRunner.runInTransaction(() ->
                deliveries.claimPending(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR,
                        properties.getWorkerId(), batchSize));
```

- [ ] **Step 4: Replace the per-delivery transaction**

In `processOne`, replace:

```java
    private ProjectionWorkerResult processOne(ProjectionWorkerResult result, OutboxDelivery delivery) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            return tx.execute(status -> processOneInTransaction(result, delivery));
        } catch (RuntimeException e) {
            return handleFailure(result, delivery, e);
        }
    }
```

with:

```java
    private ProjectionWorkerResult processOne(ProjectionWorkerResult result, OutboxDelivery delivery) {
        try {
            return transactionRunner.runInTransaction(() -> processOneInTransaction(result, delivery));
        } catch (RuntimeException e) {
            return handleFailure(result, delivery, e);
        }
    }
```

- [ ] **Step 5: Compile to verify nothing else references transactionManager**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorker.java
git commit -m "refactor(projection): use TransactionRunner port in worker"
```

---

## Task 3: StaleDeliveryRecovery depends on TransactionRunner

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/StaleDeliveryRecovery.java`

- [ ] **Step 1: Replace imports and field**

In `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/StaleDeliveryRecovery.java`, replace:

```java
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
```

with:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
```

Then replace:

```java
    private final PlatformTransactionManager transactionManager;
```

with:

```java
    private final TransactionRunner transactionRunner;
```

- [ ] **Step 2: Replace the recovery body**

Replace:

```java
    public int recover() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status ->
                deliveries.recoverStaleProcessing(properties.getProcessingTimeout(), 100));
    }
```

with:

```java
    public int recover() {
        return transactionRunner.runInTransaction(() ->
                deliveries.recoverStaleProcessing(properties.getProcessingTimeout(), 100));
    }
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/projection/StaleDeliveryRecovery.java
git commit -m "refactor(projection): use TransactionRunner port in stale recovery"
```

---

## Task 4: Tighten ArchUnit to block non-allow-listed Spring in core

**Files:**
- Modify: `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java`

- [ ] **Step 1: Add a new rule that denies Spring infrastructure in core**

Open `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java` and add the following import near the other `ArchRuleDefinition` imports at the top:

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
```

Then add the new rule as an `@ArchTest`-annotated field at the bottom of the class:

```java
    @ArchTest
    private static final ArchRule core_must_not_depend_on_spring_infrastructure_outside_allow_list = noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.transaction.support..",
                    "org.springframework.transaction.PlatformTransactionManager",
                    "org.springframework.jdbc..",
                    "org.springframework.web..",
                    "org.springframework.boot.."
            )
            .because("core must depend on framework integrations only through ports; the only Spring packages allowed in core are org.springframework.stereotype.. and org.springframework.transaction.annotation..");
```

- [ ] **Step 2: Run the ArchUnit tests**

Run: `./gradlew qualityTest`
Expected: `BUILD SUCCESSFUL`.

If the rule fires because the production code still references `PlatformTransactionManager` (Tasks 2 and 3 must be merged first), re-check those tasks are committed.

- [ ] **Step 3: Verify the rule actually catches leaks**

Temporarily add a throwaway class to confirm the rule works. Create `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/_ArchLeakProbe.java` with:

```java
package com.sanmoo.eventsourcing.creditaccount.quality;

import org.springframework.transaction.support.TransactionTemplate;

class _ArchLeakProbe {
    @SuppressWarnings("unused")
    private final TransactionTemplate leaking = null;
}
```

Run: `./gradlew qualityTest`
Expected: `BUILD FAILED` with a violation referencing `_ArchLeakProbe` and `TransactionTemplate`.

- [ ] **Step 4: Remove the probe**

```bash
rm src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/_ArchLeakProbe.java
```

Run: `./gradlew qualityTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the new rule**

```bash
git add src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java
git commit -m "test(architecture): deny non-allow-listed Spring in core"
```

---

## Task 5: Update ProjectionWorkerTest to use TransactionRunner

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java`

`ProjectionWorker` now requires a `TransactionRunner`. The current test uses a `PlatformTransactionManager` mock; it must be replaced with a `TransactionRunner` mock.

- [ ] **Step 1: Update imports**

Replace:

```java
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
```

with:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
```

- [ ] **Step 2: Replace the `mockTx()` helper**

Replace the `mockTx()` helper at the bottom of the class with:

```java
    private TransactionRunner txRunner() {
        return mock(TransactionRunner.class);
    }
```

- [ ] **Step 3: Replace usages of `mockTx()`**

In both `processOnce_appliesPendingEvent` and `processOnce_blocksOnGap`, replace:

```java
mockTx()
```

with:

```java
txRunner()
```

The `TransactionRunner` mock has a default behavior of returning `null`. To make claim succeed, stub the runner to return what the repository returns. Add a `when(...).thenReturn(...)` chain that mirrors the existing `claimPending` stub by chaining:

```java
when(txRunner().runInTransaction(any())).thenAnswer(invocation -> {
    java.util.function.Supplier<?> supplier = invocation.getArgument(0);
    return supplier.get();
});
```

The simplest approach is to stub the `TransactionRunner` so it executes the supplier directly, which preserves current behavior:

```java
        var txRunner = txRunner();
        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProjectionWorkerResult> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, txRunner);
```

Add this block in each test before constructing `worker`. Adjust the call so the runner executes the supplied action.

- [ ] **Step 4: Run the tests**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorkerTest"`
Expected: `BUILD SUCCESSFUL` with all `ProjectionWorkerTest` tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java
git commit -m "test(projection): adapt ProjectionWorkerTest to TransactionRunner"
```

---

## Task 6: Add ProjectionWorkerResultTest

**Files:**
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerResultTest.java`

- [ ] **Step 1: Create the test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerResultTest.java` with:

```java
package com.sanmoo.eventsourcing.creditaccount.core.projection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionWorkerResultTest {

    @Test
    void empty_hasAllCountersZero() {
        var result = ProjectionWorkerResult.empty();
        assertThat(result.claimed()).isZero();
        assertThat(result.processed()).isZero();
        assertThat(result.blocked()).isZero();
        assertThat(result.retried()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void withClaimed_replacesClaimedOnly() {
        var result = ProjectionWorkerResult.empty().withClaimed(3);
        assertThat(result.claimed()).isEqualTo(3);
        assertThat(result.processed()).isZero();
    }

    @Test
    void plusCounters_accumulateIndependently() {
        var result = ProjectionWorkerResult.empty()
                .withClaimed(2)
                .plusProcessed(1)
                .plusBlocked(1)
                .plusRetried(1)
                .plusFailed(1);
        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.blocked()).isEqualTo(1);
        assertThat(result.retried()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorkerResultTest"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerResultTest.java
git commit -m "test(projection): add ProjectionWorkerResult coverage"
```

---

## Task 7: Add ProjectionGatingResultTest

**Files:**
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionGatingResultTest.java`

- [ ] **Step 1: Create the test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionGatingResultTest.java` with:

```java
package com.sanmoo.eventsourcing.creditaccount.core.projection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectionGatingResultTest {

    @Test
    void apply_hasNoReason() {
        var result = ProjectionGatingResult.apply();
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.APPLY);
        assertThat(result.reason()).isNull();
    }

    @Test
    void alreadyApplied_hasNoReason() {
        var result = ProjectionGatingResult.alreadyApplied();
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.ALREADY_APPLIED);
        assertThat(result.reason()).isNull();
    }

    @Test
    void blocked_requiresNonNullReason() {
        assertThatThrownBy(() -> ProjectionGatingResult.blocked(null))
                .isInstanceOf(NullPointerException.class);
        var result = ProjectionGatingResult.blocked("gap detected");
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.BLOCKED);
        assertThat(result.reason()).isEqualTo("gap detected");
    }

    @Test
    void permanentFailure_requiresNonNullReason() {
        assertThatThrownBy(() -> ProjectionGatingResult.permanentFailure(null))
                .isInstanceOf(NullPointerException.class);
        var result = ProjectionGatingResult.permanentFailure("bad version");
        assertThat(result.decision()).isEqualTo(ProjectionGatingResult.Decision.PERMANENT_FAILURE);
        assertThat(result.reason()).isEqualTo("bad version");
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionGatingResultTest"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionGatingResultTest.java
git commit -m "test(projection): add ProjectionGatingResult coverage"
```

---

## Task 8: Add StaleDeliveryRecoveryTest

**Files:**
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/StaleDeliveryRecoveryTest.java`

- [ ] **Step 1: Create the test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/StaleDeliveryRecoveryTest.java` with:

```java
package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxDeliveryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StaleDeliveryRecoveryTest {

    @Test
    void recover_invokesRepositoryInsideTransaction() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var txRunner = mock(TransactionRunner.class);
        var props = new ProjectionProperties();
        props.setProcessingTimeout(Duration.ofMinutes(2));

        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            Supplier<Integer> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(deliveries.recoverStaleProcessing(eq(props.getProcessingTimeout()), eq(100))).thenReturn(7);

        var recovery = new StaleDeliveryRecovery(deliveries, props, txRunner);
        int recovered = recovery.recover();

        assertThat(recovered).isEqualTo(7);
        verify(deliveries).recoverStaleProcessing(Duration.ofMinutes(2), 100);
    }

    @Test
    void recover_returnsZeroWhenNothingStale() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var txRunner = mock(TransactionRunner.class);

        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            Supplier<Integer> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(deliveries.recoverStaleProcessing(any(), anyInt())).thenReturn(0);

        var recovery = new StaleDeliveryRecovery(deliveries, new ProjectionProperties(), txRunner);
        assertThat(recovery.recover()).isZero();
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.projection.StaleDeliveryRecoveryTest"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/StaleDeliveryRecoveryTest.java
git commit -m "test(projection): add StaleDeliveryRecovery coverage"
```

---

## Task 9: Extend ProjectionWorkerTest with permanent failure, retry, and backoff scenarios

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java`

- [ ] **Step 1: Add a permanent-failure scenario**

Inside `ProjectionWorkerTest`, add a new test:

```java
    @Test
    void processOnce_marksPermanentFailureOnGatingDecision() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();
        var txRunner = txRunner();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 0L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR,
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProjectionWorkerResult> s = invocation.getArgument(0);
            return s.get();
        });

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, txRunner);

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.failed()).isEqualTo(1);
        verify(deliveries).markPermanentFailure(eq(eventId), eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR),
                eq(1), anyString());
    }
```

- [ ] **Step 2: Add an already-applied scenario**

```java
    @Test
    void processOnce_marksAlreadyApplied() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();
        var txRunner = txRunner();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 2L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR,
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(checkpoints.find(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new ProjectionCheckpoint(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR,
                        "CreditAccount", aggregateId.toString(), 5L, UUID.randomUUID(), Instant.now())));
        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProjectionWorkerResult> s = invocation.getArgument(0);
            return s.get();
        });

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, txRunner);

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.processed()).isEqualTo(1);
        verify(deliveries).markProcessed(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR);
    }
```

- [ ] **Step 3: Add a transient-failure retry scenario**

```java
    @Test
    void processOnce_retriesOnTransientFailure() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();
        var txRunner = txRunner();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 1L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR,
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(checkpoints.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(summaries.findById(any())).thenThrow(new RuntimeException("boom"));
        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProjectionWorkerResult> s = invocation.getArgument(0);
            return s.get();
        });

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, txRunner);

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.retried()).isEqualTo(1);
        verify(deliveries).markRetryableFailure(eq(eventId), eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR),
                eq(1), eq(10), anyString(), any());
    }
```

- [ ] **Step 4: Add a permanent-failure on max-attempts scenario**

```java
    @Test
    void processOnce_marksPermanentFailureOnMaxAttempts() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();
        var txRunner = txRunner();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 1L,
                "CreditAccountOpened",
                new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now()),
                java.util.Map.of(), Instant.now());
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR,
                OutboxDeliveryStatus.PROCESSING, 9, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(checkpoints.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(summaries.findById(any())).thenThrow(new RuntimeException("boom"));
        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProjectionWorkerResult> s = invocation.getArgument(0);
            return s.get();
        });

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, txRunner);

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.failed()).isEqualTo(1);
        verify(deliveries).markPermanentFailure(eq(eventId), eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR),
                eq(10), anyString());
    }
```

- [ ] **Step 5: Add an empty-claim scenario**

```java
    @Test
    void processOnce_emptyClaimReturnsEmptyResult() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();
        var txRunner = txRunner();

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR), anyString(), anyInt()))
                .thenReturn(List.of());
        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<List<com.sanmoo.eventsourcing.creditaccount.core.port.model.OutboxDelivery>> s =
                    invocation.getArgument(0);
            return s.get();
        });

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, txRunner);

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.claimed()).isZero();
        assertThat(result.processed()).isZero();
        assertThat(result.blocked()).isZero();
        assertThat(result.retried()).isZero();
        assertThat(result.failed()).isZero();
    }
```

- [ ] **Step 6: Add an apply-with-current-summary scenario (verifies unblockNextVersion)**

```java
    @Test
    void processOnce_applyUsesCurrentSummaryAndUnblocksNextVersion() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var summaries = mock(CreditAccountSummaryRepository.class);
        var checkpoints = mock(ProjectionCheckpointRepository.class);
        var eventLoader = mock(OutboxEventLoader.class);
        var projector = new CreditAccountSummaryProjector();
        var txRunner = txRunner();

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var opened = new CreditAccountOpened(CreditAccountId.of(aggregateId), Instant.now());
        var event = new OutboxEvent(eventId, "CreditAccount", aggregateId.toString(), 1L,
                "CreditAccountOpened", opened, java.util.Map.of(), Instant.now());
        var delivery = new OutboxDelivery(eventId, ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR,
                OutboxDeliveryStatus.PROCESSING, 0, 10, Instant.now(), Instant.now(), "w",
                null, null, null, null, null, Instant.now(), Instant.now());
        var existing = new CreditAccountSummary(CreditAccountId.of(aggregateId), false, null,
                "0.00", "0.00", "0.00", List.of(), 0L, null, Instant.now());

        when(deliveries.claimPending(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR), anyString(), anyInt()))
                .thenReturn(List.of(delivery));
        when(eventLoader.findById(eventId)).thenReturn(Optional.of(event));
        when(checkpoints.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(summaries.findById(any())).thenReturn(Optional.of(existing));
        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            java.util.function.Supplier<ProjectionWorkerResult> s = invocation.getArgument(0);
            return s.get();
        });

        var props = new ProjectionProperties();
        props.setWorkerId("w");
        var worker = new ProjectionWorker(deliveries, summaries, checkpoints, eventLoader, projector,
                new ProjectionGating(), props, txRunner);

        ProjectionWorkerResult result = worker.processOnce(10);
        assertThat(result.processed()).isEqualTo(1);
        verify(summaries).upsert(argThat(s -> s.opened() && s.projectedVersion() == 1L));
        verify(checkpoints).upsert(argThat(cp -> cp.lastProjectedVersion() == 1L && cp.lastEventId().equals(eventId)));
        verify(deliveries).unblockNextVersion(eq(ConsumerNames.CREDIT_ACCOUNT_SUMMARY_PROJECTOR),
                eq("CreditAccount"), eq(aggregateId.toString()), eq(2L));
    }
```

- [ ] **Step 7: Run all `ProjectionWorkerTest` tests**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorkerTest"`
Expected: `BUILD SUCCESSFUL` with all new and existing tests passing.

- [ ] **Step 8: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java
git commit -m "test(projection): cover worker gating, retry and unblock paths"
```

---

## Task 10: Extend CreditAccountSummaryProjectorTest

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java`

- [ ] **Step 1: Add a CreditLimitChanged scenario**

Add to `CreditAccountSummaryProjectorTest`:

```java
    @Test
    void apply_changedLimitRecomputesAvailable() {
        var accountId = UUID.randomUUID();
        var openedEvent = openedEvent(accountId, 1L);
        var opened = projector.apply(openedEvent, projector.emptySummary(openedEvent));

        var assigned = new CreditLimitAssigned(CreditAccountId.of(accountId), Money.of("500.00"), Instant.now());
        var assignedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2L,
                "CreditLimitAssigned", assigned, java.util.Map.of(), Instant.now());
        var assigned = projector.apply(assignedEvent, opened);

        var changed = new com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitChanged(
                CreditAccountId.of(accountId), Money.of("800.00"), Instant.now());
        var changedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3L,
                "CreditLimitChanged", changed, java.util.Map.of(), Instant.now());
        var after = projector.apply(changedEvent, assigned);

        assertThat(after.creditLimit()).isEqualTo("800.00");
        assertThat(after.availableLimit()).isEqualTo("800.00");
        assertThat(after.projectedVersion()).isEqualTo(3L);
    }
```

- [ ] **Step 2: Add an unknown event scenario**

```java
    @Test
    void apply_unknownEventLeavesSummaryUnchanged() {
        var accountId = UUID.randomUUID();
        var openedEvent = openedEvent(accountId, 1L);
        var base = projector.apply(openedEvent, projector.emptySummary(openedEvent));
        var unknown = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2L,
                "UnknownEvent", new java.util.HashMap<>(), java.util.Map.of(), Instant.now());
        var after = projector.apply(unknown, base);
        assertThat(after).isEqualTo(base);
    }
```

Note: if `CreditAccountEvent` cannot be a `Map`, adapt the unknown event to a real subtype (e.g., a payment received with money `0`). The intent is to assert that the projector does not modify the summary for events it does not understand.

- [ ] **Step 3: Add a `PaymentReceived` scenario**

```java
    @Test
    void apply_paymentReceivedReducesOutstanding() {
        var accountId = UUID.randomUUID();
        var openedEvent = openedEvent(accountId, 1L);
        var opened = projector.apply(openedEvent, projector.emptySummary(openedEvent));
        var payment = new com.sanmoo.eventsourcing.creditaccount.domain.event.PaymentReceived(
                CreditAccountId.of(accountId), Money.of("100.00"), Instant.now());
        var paymentEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2L,
                "PaymentReceived", payment, java.util.Map.of(), Instant.now());
        var after = projector.apply(paymentEvent, opened);
        assertThat(after.outstandingBalance()).isEqualTo("-100.00");
        assertThat(after.projectedVersion()).isEqualTo(2L);
    }
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.projection.CreditAccountSummaryProjectorTest"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java
git commit -m "test(projection): cover projector limit change, payment and unknown events"
```

---

## Task 11: Run the full quality gateway

**Files:** none

- [ ] **Step 1: Run `./gradlew check`**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`. If PIT fails below 80%:

- inspect `build/reports/pitest/index.html`;
- add focused tests for the surviving mutations;
- re-run.

- [ ] **Step 2: Run the acceptance tests**

Run: `./gradlew acceptanceTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Final commit if any new test files remain uncommitted**

```bash
git status
git add <any-uncommitted-changes>
git commit -m "test: finalize projection quality gateway coverage"
```

---

## Notes

- If PIT survivors reveal areas outside `core.projection` (e.g., `core.usecase`), the implementation may add narrow tests but must not adjust the threshold or broadly exclude packages.
- The probe class in Task 4 is intentionally a manual verification step. If automated verification is desired, replace it with an ArchUnit `tryImport...` style test (out of scope for this plan).
- All work must be committed step-by-step so reviewers can bisect the change.
