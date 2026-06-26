# Concurrency Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic, use-case-level concurrency tests that verify optimistic-locking conflicts, idempotent replay, projection gating, and read-your-writes consistency.

**Architecture:** A reusable `ConcurrentCommandHarness` submits commands concurrently, retries `ConcurrencyConflictException` sequentially, and returns aggregated results. Integration tests invoke Spring use cases against a real PostgreSQL instance via Testcontainers and assert domain invariants.

**Tech Stack:** Java 25, Spring Boot 4.0.6, JUnit 5, AssertJ, Testcontainers PostgreSQL 18, Gradle Kotlin DSL.

## Global Constraints

- Java toolchain version: 25.
- Test method names use `snake_case` per project convention.
- No production behavior changes; retry stays in test code only.
- All new test files live under `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/`.
- Tests run deterministically: same outcome on every run.
- Build must pass with `./gradlew check`.

---

## File Structure

- `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/ConcurrentCommandResult.java` — record holding results of a concurrent run.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/ConcurrentCommandHarness.java` — submits commands, collects results/exceptions, retries conflicts.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java` — integration tests for all concurrency scenarios.

---

### Task 1: Create ConcurrentCommandResult record

**Files:**

- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/ConcurrentCommandResult.java`

**Interfaces:**

- Produces: `ConcurrentCommandResult<T>` record used by the harness and tests.

- [ ] **Step 1: Write the record**

```java
package com.sanmoo.eventsourcing.creditaccount.concurrency;

import java.util.List;

public record ConcurrentCommandResult<T>(
    List<T> successfulResults,
    int conflictCount,
    int failureCount,
    int retryCount
) {
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileTestJava`
Expected: BUILD SUCCESSFUL (record compiles; no usages yet).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/ConcurrentCommandResult.java
git commit -m "test(concurrency): add ConcurrentCommandResult record"
```

---

### Task 2: Create ConcurrentCommandHarness

**Files:**

- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/ConcurrentCommandHarness.java`

**Interfaces:**

- Consumes: `ConcurrentCommandResult<T>`.
- Produces: `ConcurrentCommandHarness` with method `<T> ConcurrentCommandResult<T> runConcurrently(List<Callable<T>> commands, int maxRetries)`.

- [ ] **Step 1: Write a failing test for the harness**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/ConcurrentCommandHarnessTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

class ConcurrentCommandHarnessTest {

    @Test
    void runConcurrently_executes_all_callables() throws Exception {
        var harness = new ConcurrentCommandHarness(2);
        List<Callable<String>> commands = List.of(() -> "a", () -> "b");

        ConcurrentCommandResult<String> result = harness.runConcurrently(commands, 3);

        assertThat(result.successfulResults()).containsExactlyInAnyOrder("a", "b");
        assertThat(result.conflictCount()).isZero();
        assertThat(result.failureCount()).isZero();
        assertThat(result.retryCount()).isZero();
    }
}
```

- [ ] **Step 2: Run the failing test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.concurrency.ConcurrentCommandHarnessTest"`
Expected: FAIL — `ConcurrentCommandHarness` class not found.

- [ ] **Step 3: Implement the harness**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/ConcurrentCommandHarness.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.concurrency;

import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ConcurrentCommandHarness implements AutoCloseable {

    private final ExecutorService executor;

    public ConcurrentCommandHarness(int threadCount) {
        this.executor = Executors.newFixedThreadPool(threadCount);
    }

    public <T> ConcurrentCommandResult<T> runConcurrently(List<Callable<T>> commands, int maxRetries) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        for (Callable<T> command : commands) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return command.call();
            }));
        }

        startLatch.countDown();

        List<T> successfulResults = new ArrayList<>();
        int conflictCount = 0;
        int failureCount = 0;
        int retryCount = 0;
        List<Callable<T>> toRetry = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            Future<T> future = futures.get(i);
            Callable<T> command = commands.get(i);
            try {
                successfulResults.add(future.get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ConcurrencyConflictException) {
                    conflictCount++;
                    toRetry.add(command);
                } else {
                    failureCount++;
                }
            }
        }

        for (Callable<T> command : toRetry) {
            T result = retry(command, maxRetries);
            if (result != null) {
                successfulResults.add(result);
                retryCount++;
            } else {
                failureCount++;
            }
        }

        return new ConcurrentCommandResult<>(successfulResults, conflictCount, failureCount, retryCount);
    }

    private <T> T retry(Callable<T> command, int maxRetries) throws InterruptedException {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return command.call();
            } catch (ConcurrencyConflictException e) {
                Thread.sleep(10L);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 4: Run the harness test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.concurrency.ConcurrentCommandHarnessTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/
git commit -m "test(concurrency): add ConcurrentCommandHarness"
```

---

### Task 3: Create base concurrency integration test class

**Files:**

- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java`

**Interfaces:**

- Consumes: `ConcurrentCommandHarness`, `ConcurrentCommandResult`, Spring use cases, `TestcontainersConfiguration`.
- Produces: integration test class with shared setup and helper methods.

- [ ] **Step 1: Create the integration test skeleton**

```java
package com.sanmoo.eventsourcing.creditaccount.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.AssignCreditLimitUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.AuthorizePurchaseUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.CapturePurchaseUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.GetCreditAccountUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.OpenCreditAccountUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ReceivePaymentUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ReleasePurchaseAuthorizationUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AssignCreditLimitInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CapturePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.OpenCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReceivePaymentInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReleasePurchaseAuthorizationInput;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CreditAccountUseCaseConcurrencyIT {

    @Autowired
    private OpenCreditAccountUseCase openCreditAccountUseCase;

    @Autowired
    private AssignCreditLimitUseCase assignCreditLimitUseCase;

    @Autowired
    private AuthorizePurchaseUseCase authorizePurchaseUseCase;

    @Autowired
    private CapturePurchaseUseCase capturePurchaseUseCase;

    @Autowired
    private ReleasePurchaseAuthorizationUseCase releasePurchaseAuthorizationUseCase;

    @Autowired
    private ReceivePaymentUseCase receivePaymentUseCase;

    @Autowired
    private GetCreditAccountUseCase getCreditAccountUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CreditAccountId accountId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM projection_checkpoints");
        jdbcTemplate.update("DELETE FROM credit_account_summary");
        jdbcTemplate.update("DELETE FROM outbox_deliveries");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM idempotency_records");
        jdbcTemplate.update("DELETE FROM event_store");

        this.accountId = openCreditAccountUseCase.execute(new OpenCreditAccountInput("open-key-" + UUID.randomUUID()))
                .account().creditAccountId();
        assignCreditLimitUseCase.execute(new AssignCreditLimitInput(
                "limit-key-" + UUID.randomUUID(), accountId, Money.of("1000.00")));
    }

    @Test
    void scaffold_compiles_and_setUp_creates_account() {
        var result = getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, null));
        assertThat(result.account().creditLimit()).isEqualTo("1000.00");
    }
}
```

- [ ] **Step 2: Run the scaffold test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.concurrency.CreditAccountUseCaseConcurrencyIT.scaffold_compiles_and_setUp_creates_account"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java
git commit -m "test(concurrency): add base concurrency integration test class"
```

---

### Task 4: Add concurrent authorizations scenario

**Files:**

- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java`

**Interfaces:**

- Consumes: `ConcurrentCommandHarness`, `AuthorizePurchaseUseCase`, `GetCreditAccountUseCase`.

- [ ] **Step 1: Add helper methods and the test**

Append to `CreditAccountUseCaseConcurrencyIT`:

```java
    private Callable<CreditAccountOutput> authorize(String idempotencyKey, String authorizationId, String amount) {
        return () -> authorizePurchaseUseCase.execute(new AuthorizePurchaseInput(
                idempotencyKey,
                accountId,
                AuthorizationId.of(UUID.fromString(authorizationId)),
                Money.of(amount),
                "Merchant"
        )).account();
    }

    @Test
    void concurrent_authorizations_on_same_aggregate_eventually_succeed() throws Exception {
        int commandCount = 5;
        var commands = new java.util.ArrayList<Callable<CreditAccountOutput>>();
        for (int i = 0; i < commandCount; i++) {
            commands.add(authorize(
                    "auth-key-" + i + "-" + UUID.randomUUID(),
                    "018f5f4b-6a3c-7000-8000-%012d".formatted(i + 1),
                    "100.00"
            ));
        }

        try (var harness = new ConcurrentCommandHarness(commandCount)) {
            ConcurrentCommandResult<CreditAccountOutput> result = harness.runConcurrently(commands, 10);

            assertThat(result.successfulResults()).hasSize(commandCount);
            assertThat(result.retryCount()).isEqualTo(result.conflictCount());

            var finalAccount = getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, null));
            assertThat(finalAccount.account().authorizedAmount()).isEqualTo("500.00");
            assertThat(finalAccount.account().availableLimit()).isEqualTo("500.00");
            assertThat(new BigDecimal(finalAccount.account().authorizedAmount())
                    .add(new BigDecimal(finalAccount.account().outstandingBalance())))
                    .isLessThanOrEqualTo(new BigDecimal("1000.00"));
        }
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.concurrency.CreditAccountUseCaseConcurrencyIT.concurrent_authorizations_on_same_aggregate_eventually_succeed"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java
git commit -m "test(concurrency): add concurrent authorizations scenario"
```

---

### Task 5: Add authorization + capture scenario

**Files:**

- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java`

**Interfaces:**

- Consumes: `AuthorizePurchaseUseCase`, `CapturePurchaseUseCase`, `ConcurrentCommandHarness`.

- [ ] **Step 1: Add the test**

Append to the test class:

```java
    @Test
    void concurrent_authorization_and_capture_eventually_leaves_captured_state() throws Exception {
        String authorizationId = "018f5f4b-6a3c-7000-8000-000000000999";

        Callable<CreditAccountOutput> authorizeCommand = authorize(
                "auth-capture-key-auth", authorizationId, "150.00");
        Callable<CreditAccountOutput> captureCommand = () -> capturePurchaseUseCase.execute(new CapturePurchaseInput(
                "auth-capture-key-capture",
                accountId,
                AuthorizationId.of(UUID.fromString(authorizationId))
        )).account();

        try (var harness = new ConcurrentCommandHarness(2)) {
            ConcurrentCommandResult<CreditAccountOutput> result = harness.runConcurrently(
                    List.of(authorizeCommand, captureCommand), 10);

            assertThat(result.successfulResults()).hasSize(2);

            var finalAccount = getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, null));
            assertThat(finalAccount.account().outstandingBalance()).isEqualTo("150.00");
            assertThat(finalAccount.account().authorizedAmount()).isEqualTo("0.00");
        }
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.concurrency.CreditAccountUseCaseConcurrencyIT.concurrent_authorization_and_capture_eventually_leaves_captured_state"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java
git commit -m "test(concurrency): add authorization and capture scenario"
```

---

### Task 6: Add idempotent duplicate requests scenario

**Files:**

- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java`

**Interfaces:**

- Consumes: `AuthorizePurchaseUseCase`, `ConcurrentCommandHarness`.

- [ ] **Step 1: Add the test**

Append to the test class:

```java
    @Test
    void concurrent_duplicate_idempotent_requests_process_once_and_replay() throws Exception {
        String authorizationId = "018f5f4b-6a3c-7000-8000-000000000111";
        String idempotencyKey = "duplicate-key-" + UUID.randomUUID();
        var input = new AuthorizePurchaseInput(
                idempotencyKey,
                accountId,
                AuthorizationId.of(UUID.fromString(authorizationId)),
                Money.of("75.00"),
                "Merchant"
        );

        Callable<com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseOutput> command =
                () -> authorizePurchaseUseCase.execute(input);

        try (var harness = new ConcurrentCommandHarness(3)) {
            ConcurrentCommandResult<com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseOutput> result =
                    harness.runConcurrently(List.of(command, command, command), 10);

            assertThat(result.successfulResults()).hasSize(3);

            long replayedCount = result.successfulResults().stream().filter(
                    com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseOutput::replayed).count();
            assertThat(replayedCount).isEqualTo(2);

            var first = result.successfulResults().getFirst();
            assertThat(result.successfulResults())
                    .allMatch(output -> output.authorizationId().equals(first.authorizationId()));

            var finalAccount = getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, null));
            assertThat(finalAccount.account().authorizedAmount()).isEqualTo("75.00");
        }
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.concurrency.CreditAccountUseCaseConcurrencyIT.concurrent_duplicate_idempotent_requests_process_once_and_replay"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java
git commit -m "test(concurrency): add idempotent duplicate requests scenario"
```

---

### Task 7: Add write + read with minVersion scenario

**Files:**

- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java`

**Interfaces:**

- Consumes: `AuthorizePurchaseUseCase`, `GetCreditAccountUseCase`, `ProjectionWorker` (autowired from existing tests).

- [ ] **Step 1: Autowire ProjectionWorker and add the test**

Add to the class fields:

```java
    @Autowired
    private com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker projectionWorker;
```

Append to the test class:

```java
    @Test
    void concurrent_write_and_read_with_minVersion_is_consistent() throws Exception {
        String authorizationId = "018f5f4b-6a3c-7000-8000-000000000222";

        Callable<CreditAccountOutput> writeCommand = authorize(
                "minversion-write-key", authorizationId, "200.00");
        Callable<CreditAccountOutput> readCommand = () -> {
            projectionWorker.processOnce(50);
            return getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, 3L)).account();
        };

        try (var harness = new ConcurrentCommandHarness(2)) {
            ConcurrentCommandResult<CreditAccountOutput> result = harness.runConcurrently(
                    List.of(writeCommand, readCommand), 10);

            assertThat(result.successfulResults()).hasSize(2);

            var readOutput = result.successfulResults().stream()
                    .filter(output -> output.projectedVersion() >= 3L)
                    .findFirst()
                    .orElseThrow();
            assertThat(readOutput.authorizedAmount()).isEqualTo("200.00");
        }
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.concurrency.CreditAccountUseCaseConcurrencyIT.concurrent_write_and_read_with_minVersion_is_consistent"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java
git commit -m "test(concurrency): add write and read with minVersion scenario"
```

---

### Task 8: Add mixed commands scenario

**Files:**

- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java`

**Interfaces:**

- Consumes: all use cases, `ConcurrentCommandHarness`.

- [ ] **Step 1: Add the test**

Append to the test class:

```java
    @Test
    void concurrent_mixed_commands_maintain_invariants() throws Exception {
        String authId = "018f5f4b-6a3c-7000-8000-000000000333";

        authorizePurchaseUseCase.execute(new AuthorizePurchaseInput(
                "mixed-setup-key", accountId, AuthorizationId.of(UUID.fromString(authId)),
                Money.of("300.00"), "Merchant"));

        var commands = List.of(
                (Callable<CreditAccountOutput>) () -> capturePurchaseUseCase.execute(new CapturePurchaseInput(
                        "mixed-capture", accountId, AuthorizationId.of(UUID.fromString(authId)))).account(),
                () -> receivePaymentUseCase.execute(new ReceivePaymentInput(
                        "mixed-payment", accountId, Money.of("50.00"))).account(),
                () -> releasePurchaseAuthorizationUseCase.execute(new ReleasePurchaseAuthorizationInput(
                        "mixed-release", accountId, AuthorizationId.of(UUID.fromString(authId)))).account()
        );

        try (var harness = new ConcurrentCommandHarness(commands.size())) {
            ConcurrentCommandResult<CreditAccountOutput> result = harness.runConcurrently(commands, 10);

            assertThat(result.successfulResults()).hasSizeGreaterThanOrEqualTo(1);

            var finalAccount = getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, null));
            BigDecimal outstanding = new BigDecimal(finalAccount.account().outstandingBalance());
            BigDecimal authorized = new BigDecimal(finalAccount.account().authorizedAmount());
            BigDecimal limit = new BigDecimal(finalAccount.account().creditLimit());

            assertThat(outstanding).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(authorized).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(outstanding.add(authorized)).isLessThanOrEqualTo(limit);
        }
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.concurrency.CreditAccountUseCaseConcurrencyIT.concurrent_mixed_commands_maintain_invariants"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/CreditAccountUseCaseConcurrencyIT.java
git commit -m "test(concurrency): add mixed commands scenario"
```

---

### Task 9: Run full test suite and quality gate

**Files:**

- No file changes.

- [ ] **Step 1: Run all concurrency tests**

Run: `./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.concurrency.*"`
Expected: All 6 tests PASS.

- [ ] **Step 2: Run full build**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit any remaining changes**

If no changes, skip. Otherwise:

```bash
git add -A
git commit -m "test(concurrency): finalize concurrency test suite"
```

---

## Self-Review

- **Spec coverage:**
  - Concurrent authorizations → Task 4.
  - Authorization + capture → Task 5.
  - Idempotent duplicate requests → Task 6.
  - Write + read with minVersion → Task 7.
  - Mixed commands → Task 8.
  - Retry only in tests → explicit in Global Constraints and harness implementation.
  - Deterministic behavior → harness uses `CountDownLatch` and sequential retry.
- **Placeholder scan:** No TBD, TODO, or vague steps.
- **Type consistency:** `ConcurrentCommandResult<T>` is used consistently; use case input/output types match production code.
