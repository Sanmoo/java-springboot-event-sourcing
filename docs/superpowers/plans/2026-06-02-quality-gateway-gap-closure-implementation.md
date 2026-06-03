# Quality Gateway Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining quality gateway gaps by making PMD blocking and raising PITest mutation coverage to at least 80% while keeping all existing gates green.

**Architecture:** Keep the existing single-module Gradle setup and single `pitest` task. Calibrate PMD by removing noisy rules from the blocking gate, then add targeted application-service tests to kill surviving mutants in `CreditAccountCommandService`. Error Prone remains active but warnings-as-errors is not implemented because the current plugin does not expose direct support.

**Tech Stack:** Java 25, Gradle 9.5.1, Spring Boot 4.0.6, PMD 7.25.0, PITest 1.25.3, JUnit 5, Mockito, AssertJ.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `build.gradle.kts` | Modify | Make PMD blocking and raise PITest threshold to 80 |
| `config/pmd/pmd.xml` | Modify | Calibrate blocking PMD ruleset |
| `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandService.java` | Modify | Remove PMD `UnusedLocalVariable` noise by using Java 25 unnamed pattern variables if needed |
| `src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java` | Modify | Add targeted tests for mutation coverage gaps |

---

### Task 1: Calibrate PMD ruleset and make PMD blocking

**Files:**
- Modify: `config/pmd/pmd.xml`
- Modify: `build.gradle.kts`
- Possibly modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandService.java`

- [ ] **Step 1: Replace PMD ruleset with calibrated blocking rules**

Replace the entire contents of `config/pmd/pmd.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ruleset name="Calibrated PMD Rules"
    xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 https://pmd.sourceforge.io/ruleset_2_0_0.xsd">

    <description>
        Calibrated PMD gate for the credit-account event-sourcing project.
        The gate keeps high-signal rules and excludes style-only or noisy rules
        that conflict with the project conventions.
    </description>

    <rule ref="category/java/errorprone.xml">
        <exclude name="AvoidDuplicateLiterals"/>
        <exclude name="MissingSerialVersionUID"/>
        <exclude name="AvoidCatchingGenericException"/>
    </rule>

    <rule ref="category/java/bestpractices.xml">
        <exclude name="AvoidDuplicateLiterals"/>
    </rule>

    <rule ref="category/java/codestyle.xml">
        <exclude name="AtLeastOneConstructor"/>
        <exclude name="UseExplicitTypes"/>
        <exclude name="LocalVariableCouldBeFinal"/>
        <exclude name="MethodArgumentCouldBeFinal"/>
        <exclude name="LongVariable"/>
        <exclude name="ShortVariable"/>
        <exclude name="OnlyOneReturn"/>
        <exclude name="CommentDefaultAccessModifier"/>
        <exclude name="ControlStatementBraces"/>
        <exclude name="AvoidLiteralsInIfCondition"/>
    </rule>

    <rule ref="category/java/performance.xml"/>
</ruleset>
```

- [ ] **Step 2: If PMD reports unmatched excludes, remove only unmatched excludes**

Run:

```bash
export JAVA_HOME=/home/sanmoo/.local/share/mise/installs/java/25.0.2
./gradlew pmdMain --stacktrace
```

Expected if an exclude is invalid: PMD fails with text like:

```text
Exclude pattern '<RuleName>' did not match any rule in ruleset '<category>'
```

If that happens, edit `config/pmd/pmd.xml` and remove only that specific `<exclude name="..."/>` line. Do not remove whole categories.

- [ ] **Step 3: Make PMD blocking**

In `build.gradle.kts`, replace:

```kotlin
isIgnoreFailures = true  // current gap: PMD reports violations but does not fail the build
```

with:

```kotlin
isIgnoreFailures = false
```

- [ ] **Step 4: Fix expected PMD `UnusedLocalVariable` findings in command service**

If PMD still reports `UnusedLocalVariable` for switch branches like:

```java
case IdempotencyDecision.Started started -> executeAndStore(
```

change each unused variable to Java 25 unnamed pattern variable:

```java
case IdempotencyDecision.Started _ -> executeAndStore(
```

There are seven expected occurrences in `CreditAccountCommandService` for command methods:

- `openCreditAccount`
- `assignCreditLimit`
- `changeCreditLimit`
- `authorizePurchase`
- `capturePurchase`
- `releasePurchaseAuthorization`
- `receivePayment`

- [ ] **Step 5: Run PMD until it is green**

Run:

```bash
export JAVA_HOME=/home/sanmoo/.local/share/mise/installs/java/25.0.2
./gradlew pmdMain
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit PMD changes**

```bash
git add build.gradle.kts config/pmd/pmd.xml src/main/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandService.java
git commit -m "build: make calibrated PMD gate blocking"
```

---

### Task 2: Add targeted tests for idempotency conflict and replay error paths

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java`

- [ ] **Step 1: Ensure imports include error assertions and domain error**

Verify these imports exist; add missing imports:

```java
import com.sanmoo.eventsourcing.creditaccount.application.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: Add test for idempotency conflict on assignCreditLimit**

Add this method inside `CreditAccountCommandServiceTest`:

```java
@Test
void assignCreditLimitConflictThrowsIdempotencyConflictException() {
    CreditAccountId accountId = CreditAccountId.newId();
    AssignCreditLimitCommand command = new AssignCreditLimitCommand(
            "conflict-key", accountId, Money.of("200.00")
    );

    when(idempotencyPort.start(eq("conflict-key"), eq("AssignCreditLimit"), any(), any()))
            .thenReturn(new IdempotencyDecision.Conflict("idempotency key reused with different payload"));

    assertThatThrownBy(() -> service.assignCreditLimit(command))
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessageContaining("idempotency key reused");

    verify(eventStore, never()).loadEvents(any(), any());
    verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
    verify(idempotencyPort, never()).complete(anyString(), anyString());
}
```

- [ ] **Step 3: Add test for invalid replay payload**

Add this method:

```java
@Test
void invalidReplayPayloadThrowsRuntimeException() {
    OpenCreditAccountCommand command = new OpenCreditAccountCommand("bad-replay-key");

    when(idempotencyPort.start(eq("bad-replay-key"), eq("OpenCreditAccount"), any(), any()))
            .thenReturn(new IdempotencyDecision.Replay("not-json"));

    assertThatThrownBy(() -> service.openCreditAccount(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to deserialize idempotency response payload");

    verify(eventStore, never()).loadEvents(any(), any());
    verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
    verify(idempotencyPort, never()).complete(anyString(), anyString());
}
```

- [ ] **Step 4: Run application service tests**

```bash
export JAVA_HOME=/home/sanmoo/.local/share/mise/installs/java/25.0.2
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.application.service.CreditAccountCommandServiceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit tests**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java
git commit -m "test: cover idempotency conflict and replay error paths"
```

---

### Task 3: Add targeted tests for getAccount edge cases and rich response data

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java`

- [ ] **Step 1: Add test for missing account**

Add this method:

```java
@Test
void getAccountThrowsWhenAccountDoesNotExist() {
    String aggregateId = UUID.randomUUID().toString();

    when(eventStore.loadEvents(eq("CreditAccount"), eq(aggregateId)))
            .thenReturn(List.of());

    assertThatThrownBy(() -> service.getAccount(aggregateId))
            .isInstanceOf(AccountNotFoundException.class)
            .hasMessageContaining("Credit account not found");
}
```

- [ ] **Step 2: Add test for rich account snapshot response**

Add this method:

```java
@Test
void getAccountReturnsBalancesAndAuthorizations() {
    String aggregateId = UUID.randomUUID().toString();
    CreditAccountId accountId = CreditAccountId.of(UUID.fromString(aggregateId));
    AuthorizationId openAuthorizationId = AuthorizationId.newId();
    AuthorizationId capturedAuthorizationId = AuthorizationId.newId();

    when(eventStore.loadEvents(eq("CreditAccount"), eq(aggregateId)))
            .thenReturn(List.of(
                    envelope(0L, new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")), aggregateId),
                    envelope(1L, new CreditLimitAssigned(accountId, Money.of("500.00"), Instant.parse("2026-06-01T10:01:00Z")), aggregateId),
                    envelope(2L, new PurchaseAuthorized(accountId, openAuthorizationId, Money.of("50.00"), "Store A", Instant.parse("2026-06-01T10:02:00Z")), aggregateId),
                    envelope(3L, new PurchaseAuthorized(accountId, capturedAuthorizationId, Money.of("75.00"), "Store B", Instant.parse("2026-06-01T10:03:00Z")), aggregateId),
                    envelope(4L, new PurchaseCaptured(accountId, capturedAuthorizationId, Money.of("75.00"), Instant.parse("2026-06-01T10:04:00Z")), aggregateId),
                    envelope(5L, new PaymentReceived(accountId, Money.of("25.00"), Instant.parse("2026-06-01T10:05:00Z")), aggregateId)
            ));

    Map<String, Object> result = service.getAccount(aggregateId);

    assertThat(result)
            .containsEntry("creditAccountId", aggregateId)
            .containsEntry("opened", true)
            .containsEntry("creditLimit", "500.00")
            .containsEntry("outstandingBalance", "50.00")
            .containsEntry("authorizedAmount", "50.00")
            .containsEntry("availableLimit", "400.00");

    assertThat((List<?>) result.get("authorizations"))
            .hasSize(2);
}
```

- [ ] **Step 3: Run application service tests**

```bash
export JAVA_HOME=/home/sanmoo/.local/share/mise/installs/java/25.0.2
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.application.service.CreditAccountCommandServiceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Commit tests**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java
git commit -m "test: cover account query edge cases and rich response data"
```

---

### Task 4: Add test that successful command stores serialized idempotency response

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java`

- [ ] **Step 1: Ensure Mockito ArgumentCaptor import exists**

Add this import if missing:

```java
import org.mockito.ArgumentCaptor;
```

- [ ] **Step 2: Add test for serialized completion payload**

Add this method:

```java
@Test
void successfulCommandCompletesIdempotencyWithSerializedResult() throws Exception {
    CreditAccountId accountId = CreditAccountId.newId();
    String aggregateId = accountId.value().toString();
    AssignCreditLimitCommand command = new AssignCreditLimitCommand("serialize-key", accountId, Money.of("250.00"));

    when(idempotencyPort.start(eq("serialize-key"), eq("AssignCreditLimit"), any(), any()))
            .thenReturn(new IdempotencyDecision.Started("serialize-key"));
    when(eventStore.loadEvents(any(), any()))
            .thenReturn(List.of(
                    envelope(0L, new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")), aggregateId)
            ));
    when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
            .thenReturn(new AppendResult(2L));

    CommandResult result = service.assignCreditLimit(command);

    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(idempotencyPort).complete(eq("serialize-key"), payloadCaptor.capture());

    CommandResult stored = objectMapper.readValue(payloadCaptor.getValue(), CommandResult.class);
    assertThat(stored.aggregateId()).isEqualTo(aggregateId);
    assertThat(stored.aggregateVersion()).isEqualTo(2L);
    assertThat(stored.responseData()).containsEntry("creditLimit", "250.00");
    assertThat(result.aggregateVersion()).isEqualTo(2L);
}
```

- [ ] **Step 3: Run application service tests**

```bash
export JAVA_HOME=/home/sanmoo/.local/share/mise/installs/java/25.0.2
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.application.service.CreditAccountCommandServiceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Commit test**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java
git commit -m "test: verify idempotency completion payload serialization"
```

---

### Task 5: Raise PITest threshold to 80 and verify mutation coverage

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Raise threshold**

In `build.gradle.kts`, replace:

```kotlin
mutationThreshold = 72
```

with:

```kotlin
mutationThreshold = 80
```

- [ ] **Step 2: Run PITest**

```bash
export JAVA_HOME=/home/sanmoo/.local/share/mise/installs/java/25.0.2
./gradlew clean pitest
```

Expected:

```text
BUILD SUCCESSFUL
```

The output should report mutation score >= 80.

- [ ] **Step 3: If score is still below 80, inspect surviving mutants**

Run:

```bash
grep -R "survived" build/reports/pitest/com.sanmoo.eventsourcing.creditaccount.application.service/CreditAccountCommandService.java.html | head -40
```

Add only behavioral tests for surviving paths in `CreditAccountCommandServiceTest`. Do not lower the threshold and do not add new exclusions for meaningful service code.

- [ ] **Step 4: Commit threshold change**

```bash
git add build.gradle.kts src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java
git commit -m "test: raise PITest threshold to 80 percent"
```

---

### Task 6: Final quality gateway verification

**Files:**
- No planned edits. Only verification.

- [ ] **Step 1: Run PMD**

```bash
export JAVA_HOME=/home/sanmoo/.local/share/mise/installs/java/25.0.2
./gradlew pmdMain
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Run PITest**

```bash
export JAVA_HOME=/home/sanmoo/.local/share/mise/installs/java/25.0.2
./gradlew pitest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Run full gateway**

```bash
export JAVA_HOME=/home/sanmoo/.local/share/mise/installs/java/25.0.2
./gradlew clean check
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Verify build configuration has the required gate settings**

Run:

```bash
grep -n "isIgnoreFailures\|mutationThreshold" build.gradle.kts
```

Expected relevant lines:

```text
isIgnoreFailures = false
mutationThreshold = 80
```

- [ ] **Step 5: Commit final verification notes if any files changed**

If `git status --short` shows changes, commit them:

```bash
git add -A
git commit -m "chore: finalize quality gateway gap closure"
```

If no changes remain, do not create an empty commit.
