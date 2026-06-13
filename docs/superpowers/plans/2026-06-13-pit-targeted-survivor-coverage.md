# PIT Targeted Survivor Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise PIT from the current 70% toward/past the configured 80% threshold by adding focused tests for actual survivor/uncovered hotspots, without lowering `mutationThreshold` or broadly excluding production packages.

**Architecture:** Keep the architecture changes already made: core projection depends on `TransactionRunner` and `ProjectionConfig` ports, not Spring transaction APIs or concrete `ProjectionProperties`. This plan only adds focused tests and a pending checkstyle import fix; production code should change only if PIT exposes a real defect.

**Tech Stack:** Java 25, JUnit 5, Mockito, AssertJ, Gradle, Pitest 1.25.

**Current evidence:** After architectural fixes and broad test additions, `./gradlew check` fails only at PIT: `Generated 1172 mutations Killed 822 (70%)`, `Line Coverage 370/428 (86%)`, `Test strength 81%`. Main hotspots: `CreditAccountSummaryProjector` (122/261 killed), `ProjectionWorker` (145/210 killed), `ListCreditAccountsUseCase` (18/52 killed), and `CreditAccountUseCaseSupport` (127/163 killed).

---

## File Structure

Files modified by this plan:

- `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceHttpClient.java` — remove pending unused import so checkstyle stays green.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java` — add focused projection event scenarios.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java` — add worker ordering/drain/backoff/missing-event scenarios.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCaseTest.java` — add mapping and validation tests.
- Optional: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupportTest.java` — only if PIT remains below threshold after the first three targets.

---

## Task 1: Commit pending acceptance checkstyle fix

**Files:**
- Modify: `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceHttpClient.java`

The working tree currently has a pending fix that removes unused `java.io.IOException` from `AcceptanceHttpClient.java`. Do not leave this uncommitted.

- [ ] **Step 1: Verify the import is gone**

Run:

```bash
grep -n "java.io.IOException\|ObjectMapper" src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceHttpClient.java || true
```

Expected: no output.

- [ ] **Step 2: Run acceptance checkstyle**

Run:

```bash
./gradlew checkstyleAcceptanceTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceHttpClient.java
git commit -m "chore(checkstyle): remove unused acceptance client import"
```

---

## Task 2: Add targeted CreditAccountSummaryProjector tests

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java`

PIT evidence: `CreditAccountSummaryProjector` has high line coverage gaps and many survivors around capture/release branches and money calculations.

- [ ] **Step 1: Add imports if missing**

Ensure these imports exist:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorizationReleased;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCaptured;
import java.util.List;
```

`AuthorizationId`, `PurchaseAuthorized`, and `Money` may already exist after the previous task. Keep only actually used imports.

- [ ] **Step 2: Add helper for assigned summary**

Add this helper inside the test class:

```java
    private CreditAccountSummary assignedSummary(UUID accountId, String limit) {
        var openedEvent = openedEvent(accountId, 1L);
        var opened = projector.apply(openedEvent, projector.emptySummary(openedEvent));
        var assigned = new CreditLimitAssigned(CreditAccountId.of(accountId), Money.of(limit), Instant.now());
        var assignedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2L,
                "CreditLimitAssigned", assigned, java.util.Map.of(), Instant.now());
        return projector.apply(assignedEvent, opened);
    }
```

- [ ] **Step 3: Add capture scenario**

Add:

```java
    @Test
    void apply_purchaseCapturedMovesAuthorizedToOutstanding() {
        var accountId = UUID.randomUUID();
        var authorizationId = new AuthorizationId(UUID.randomUUID());
        var assigned = assignedSummary(accountId, "500.00");
        var auth = new PurchaseAuthorized(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), "Store", Instant.now());
        var authEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3L,
                "PurchaseAuthorized", auth, java.util.Map.of(), Instant.now());
        var authorized = projector.apply(authEvent, assigned);

        var captured = new PurchaseCaptured(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), Instant.now());
        var capturedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 4L,
                "PurchaseCaptured", captured, java.util.Map.of(), Instant.now());
        var after = projector.apply(capturedEvent, authorized);

        assertThat(after.outstandingBalance()).isEqualTo("150.00");
        assertThat(after.authorizedAmount()).isEqualTo("0.00");
        assertThat(after.availableLimit()).isEqualTo("350.00");
        assertThat(after.authorizations()).extracting(CreditAccountSummary.AuthorizationSummary::status)
                .containsExactly("CAPTURED");
        assertThat(after.projectedVersion()).isEqualTo(4L);
        assertThat(after.lastEventId()).isEqualTo(capturedEvent.eventId());
    }
```

- [ ] **Step 4: Add release scenario**

Add:

```java
    @Test
    void apply_purchaseAuthorizationReleasedRestoresAvailableLimit() {
        var accountId = UUID.randomUUID();
        var authorizationId = new AuthorizationId(UUID.randomUUID());
        var assigned = assignedSummary(accountId, "500.00");
        var auth = new PurchaseAuthorized(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), "Store", Instant.now());
        var authEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3L,
                "PurchaseAuthorized", auth, java.util.Map.of(), Instant.now());
        var authorized = projector.apply(authEvent, assigned);

        var released = new PurchaseAuthorizationReleased(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), Instant.now());
        var releasedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 4L,
                "PurchaseAuthorizationReleased", released, java.util.Map.of(), Instant.now());
        var after = projector.apply(releasedEvent, authorized);

        assertThat(after.authorizedAmount()).isEqualTo("0.00");
        assertThat(after.availableLimit()).isEqualTo("500.00");
        assertThat(after.authorizations()).extracting(CreditAccountSummary.AuthorizationSummary::status)
                .containsExactly("RELEASED");
        assertThat(after.projectedVersion()).isEqualTo(4L);
        assertThat(after.lastEventId()).isEqualTo(releasedEvent.eventId());
    }
```

- [ ] **Step 5: Add non-matching capture does not alter unrelated authorization status**

Add:

```java
    @Test
    void apply_purchaseCapturedLeavesNonMatchingAuthorizationOpen() {
        var accountId = UUID.randomUUID();
        var authorizationId = new AuthorizationId(UUID.randomUUID());
        var otherAuthorizationId = new AuthorizationId(UUID.randomUUID());
        var assigned = assignedSummary(accountId, "500.00");
        var auth = new PurchaseAuthorized(CreditAccountId.of(accountId), authorizationId,
                Money.of("150.00"), "Store", Instant.now());
        var authEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3L,
                "PurchaseAuthorized", auth, java.util.Map.of(), Instant.now());
        var authorized = projector.apply(authEvent, assigned);

        var captured = new PurchaseCaptured(CreditAccountId.of(accountId), otherAuthorizationId,
                Money.of("150.00"), Instant.now());
        var capturedEvent = new OutboxEvent(UUID.randomUUID(), "CreditAccount", accountId.toString(), 4L,
                "PurchaseCaptured", captured, java.util.Map.of(), Instant.now());
        var after = projector.apply(capturedEvent, authorized);

        assertThat(after.authorizations()).extracting(CreditAccountSummary.AuthorizationSummary::status)
                .containsExactly("OPEN");
        assertThat(after.outstandingBalance()).isEqualTo("150.00");
        assertThat(after.authorizedAmount()).isEqualTo("0.00");
    }
```

- [ ] **Step 6: Run projector tests**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.projection.CreditAccountSummaryProjectorTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/CreditAccountSummaryProjectorTest.java
git commit -m "test(projection): cover capture and release projector paths"
```

---

## Task 3: Add targeted ProjectionWorker tests

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java`

PIT evidence: `ProjectionWorker` survivors cluster around claim null/empty handling, sorting/load behavior, drain limits, error string/backoff, and `computeBackoff` cap.

- [ ] **Step 1: Add missing-event retry scenario**

Add a test where `eventLoader.findById(eventId)` returns `Optional.empty()` for a claimed delivery. Assert `markRetryableFailure(..., attempts=1, maxAttempts=10, error contains "IllegalStateException", backoff any())` and `result.retried() == 1`.

Use existing `mockTx()` and `ProjectionProperties` pattern from the file.

- [ ] **Step 2: Add null claim scenario**

Add a test where `deliveries.claimPending(...)` returns `null`. Assert all `ProjectionWorkerResult` counters are zero and no processing methods are invoked.

- [ ] **Step 3: Add max backoff cap scenario**

Add a test with delivery attempts high enough to exceed the cap: `processingAttempts=8`, `maxAttempts=20`, `props.setInitialBackoff(Duration.ofSeconds(10))`, `props.setMaxBackoff(Duration.ofSeconds(30))`, make `summaries.findById(any())` throw. Capture the `Duration` passed to `markRetryableFailure` and assert it equals `Duration.ofSeconds(30)`.

- [ ] **Step 4: Add drain limit scenario**

Add a test with two claimed deliveries for the same aggregate, versions 1 and 2. Set `props.setMaxConsecutiveEventsPerAggregate(1)`. Assert only one delivery is marked processed and result has `claimed == 2`, `processed == 1`.

- [ ] **Step 5: Add sorting scenario**

Add a test with claimed deliveries out of order for same aggregate: version 2 appears before version 1 in claim list. Provide both events from `eventLoader`. Assert `markProcessed` is invoked in order for version 1 event first, then version 2 event using Mockito `InOrder`.

- [ ] **Step 6: Run worker tests**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorkerTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/projection/ProjectionWorkerTest.java
git commit -m "test(projection): cover worker PIT survivor paths"
```

---

## Task 4: Add targeted ListCreditAccountsUseCase tests

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCaseTest.java`

PIT evidence: `ListCreditAccountsUseCase` is only 35% mutation score. The current tests validate invalid size, negative page, and repository delegation but do not validate output mapping.

- [ ] **Step 1: Add imports**

Add imports:

```java
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import java.time.Instant;
import java.util.UUID;
```

- [ ] **Step 2: Add mapping test**

Add:

```java
    @Test
    void mapsSummaryPageToOutput() {
        UUID accountId = UUID.randomUUID();
        var authorization = new CreditAccountSummary.AuthorizationSummary(
                UUID.randomUUID(), "25.00", "OPEN", "Store");
        var summary = new CreditAccountSummary(
                accountId,
                true,
                "500.00",
                "100.00",
                "25.00",
                "375.00",
                List.of(authorization),
                7L,
                UUID.randomUUID(),
                Instant.now());
        when(repo.findAll(any())).thenReturn(new CreditAccountSummaryPage(List.of(summary), 2, 10, 21, 3));

        var output = useCase.execute(new ListCreditAccountsInput(2, 10));

        assertThat(output.page()).isEqualTo(2);
        assertThat(output.size()).isEqualTo(10);
        assertThat(output.totalItems()).isEqualTo(21);
        assertThat(output.totalPages()).isEqualTo(3);
        assertThat(output.items()).hasSize(1);
        var item = output.items().get(0);
        assertThat(item.creditAccountId()).isEqualTo(accountId.toString());
        assertThat(item.opened()).isTrue();
        assertThat(item.creditLimit()).isEqualTo("500.00");
        assertThat(item.outstandingBalance()).isEqualTo("100.00");
        assertThat(item.authorizedAmount()).isEqualTo("25.00");
        assertThat(item.availableLimit()).isEqualTo("375.00");
        assertThat(item.projectedVersion()).isEqualTo(7L);
        assertThat(item.authorizations()).hasSize(1);
        assertThat(item.authorizations().get(0).authorizationId()).isEqualTo(authorization.authorizationId().toString());
        assertThat(item.authorizations().get(0).amount()).isEqualTo("25.00");
        assertThat(item.authorizations().get(0).status()).isEqualTo("OPEN");
        assertThat(item.authorizations().get(0).merchantName()).isEqualTo("Store");
    }
```

- [ ] **Step 3: Add boundary validation test**

Add:

```java
    @Test
    void acceptsMaximumPageSize() {
        when(repo.findAll(any())).thenReturn(new CreditAccountSummaryPage(List.of(), 0,
                ListCreditAccountsInput.MAX_SIZE, 0, 0));
        var output = useCase.execute(new ListCreditAccountsInput(0, ListCreditAccountsInput.MAX_SIZE));
        assertThat(output.size()).isEqualTo(ListCreditAccountsInput.MAX_SIZE);
    }
```

- [ ] **Step 4: Run tests**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.usecase.ListCreditAccountsUseCaseTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ListCreditAccountsUseCaseTest.java
git commit -m "test(usecase): cover credit account listing mapping"
```

---

## Task 5: Re-run PIT and decide whether CreditAccountUseCaseSupport needs targeted tests

**Files:**
- Optional modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupportTest.java`

- [ ] **Step 1: Run PIT**

Run:

```bash
./gradlew pitest
```

Expected target: mutation score >= 80.

- [ ] **Step 2: If PIT passes, skip to Task 6**

No code changes.

- [ ] **Step 3: If PIT is still below 80, inspect report**

Run:

```bash
python3 - <<'PY'
from pathlib import Path
import re
base=Path('build/reports/pitest')
for pkg_index in base.glob('**/index.html'):
    if pkg_index == base/'index.html':
        continue
    html=pkg_index.read_text(errors='ignore')
    cells=re.findall(r'<td[^>]*>(.*?)</td>', html, re.S)
    clean=[]
    for c in cells:
        c=re.sub(r'<[^>]+>','',c)
        c=' '.join(c.split())
        if c:
            clean.append(c)
    print('\nPACKAGE', pkg_index.parent.relative_to(base))
    for i,c in enumerate(clean):
        if c.endswith('.java'):
            print(c, clean[i+1:i+4])
PY
```

Use the output to choose the next exact class. Prefer `CreditAccountUseCaseSupport` only if it remains one of the largest mutation gaps.

- [ ] **Step 4: If needed, add support tests**

Do not write broad tests blindly. Add only tests that cover visible survivors, likely:

- idempotency replay with matching payload/version returns replayed output;
- idempotency replay with mismatched request hash throws `IdempotencyConflictException`;
- replay payload aggregate version mismatch throws runtime error;
- `loadAccountOutput` throws `AccountNotFoundException` for empty history.

- [ ] **Step 5: Commit support tests if added**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupportTest.java
git commit -m "test(usecase): cover idempotency support PIT survivors"
```

---

## Task 6: Final quality gateway

**Files:** none unless final trivial fixes are discovered.

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

- [ ] **Step 3: Record final PIT evidence**

Capture the final PIT summary lines in the implementation report:

```text
Line Coverage ...
Generated ... Killed ...
Mutation score ...
```

- [ ] **Step 4: Ensure clean git state**

Run:

```bash
git status --short
```

Expected: no output.
