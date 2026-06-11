# Aggregate-Applied Domain Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every `CreditAccount` command method apply its own emitted event and return the already-applied events, and stop having `CreditAccountUseCaseSupport` apply events manually.

**Architecture:** Introduce a private `recordThat` helper in the aggregate that applies a new event and increments version. Switch `rehydrate` to a private `applyHistorical` helper. Remove the public `applyAll`. Capture `expectedVersion` in the use case support before invoking the domain command. Add tests that prove both returned events and applied state, and verify the pre-command version is passed to `appendEvents`.

**Tech Stack:** Java 25, Spring Boot 4.0.6, JUnit 5, AssertJ, Mockito, Gradle 9.5.1.

---

## File Structure

Files to modify:

- `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java` — add `recordThat`, switch `rehydrate` to `applyHistorical`, remove public `applyAll`, refactor every command method to apply its event before returning.
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java` — capture `expectedVersion` before `executor.execute(account)`, remove `account.applyAll(newEvents)`.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java` — add state and version assertions to happy path tests.
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java` — verify the pre-command expected version is passed to `appendEvents`.
- `README.md` — add `Key Design Decisions` entry.

No new files. No public API expansions.

---

## Task 1: RED — `open` applies event internally

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`

- [ ] **Step 1: Update the failing test**

Replace the `opensCreditAccount` test body in `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java` with:

```java
@Test
void opensCreditAccount() {
    CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());

    CreditAccount account = CreditAccount.rehydrate(accountId, List.of());
    var events = account.open(Instant.parse("2026-06-01T10:00:00Z"));

    assertThat(events).containsExactly(new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")));
    assertThat(account.snapshot().opened()).isTrue();
    assertThat(account.version()).isEqualTo(1L);
}
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.opensCreditAccount
```

Expected: FAIL because `account.snapshot().opened()` is `false` and `account.version()` is `0L` after the call. The `open` method returns the event but does not apply it.

---

## Task 2: GREEN — `open` applies event

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`

- [ ] **Step 1: Add the private helper and refactor `open`**

In `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`, add a private helper:

```java
private List<CreditAccountEvent> recordThat(CreditAccountEvent event) {
    apply(event);
    version++;
    return List.of(event);
}
```

Replace the `open` method body with:

```java
public List<CreditAccountEvent> open(Instant occurredAt) {
    if (opened) { throw new AccountAlreadyExistsException("credit account already exists"); }
    return recordThat(new CreditAccountOpened(id, occurredAt));
}
```

- [ ] **Step 2: Run the test and confirm GREEN**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.opensCreditAccount
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
git commit -m "feat(domain): apply CreditAccountOpened in open"
```

---

## Task 3: RED — `assignCreditLimit` applies event internally

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`

- [ ] **Step 1: Update the failing test**

In `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`, replace the body of `assignCreditLimitOnOpenedAccount` with:

```java
@Test
void assignCreditLimitOnOpenedAccount() {
    CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
    CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
            new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z"))
    ));

    var events = account.assignCreditLimit(Money.of("500.00"), Instant.parse("2026-06-01T10:01:00Z"));

    assertThat(events).containsExactly(new CreditLimitAssigned(accountId, Money.of("500.00"), Instant.parse("2026-06-01T10:01:00Z")));
    assertThat(account.snapshot().creditLimit()).isEqualTo(Money.of("500.00"));
    assertThat(account.version()).isEqualTo(2L);
}
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.assignCreditLimitOnOpenedAccount
```

Expected: FAIL because `creditLimit` is still `null` and `version` is `1L` after the call.

---

## Task 4: GREEN — `assignCreditLimit` applies event

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`

- [ ] **Step 1: Refactor `assignCreditLimit`**

Replace the `assignCreditLimit` method body with:

```java
public List<CreditAccountEvent> assignCreditLimit(Money limit, Instant occurredAt) {
    ensureOpened();
    if (creditLimit != null) { throw new CreditLimitAlreadyAssignedException("credit limit already assigned"); }
    if (!limit.isGreaterThan(Money.zero())) { throw new InvalidCreditLimitException("credit limit must be positive"); }
    return recordThat(new CreditLimitAssigned(id, limit, occurredAt));
}
```

- [ ] **Step 2: Run the test and confirm GREEN**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.assignCreditLimitOnOpenedAccount
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
git commit -m "feat(domain): apply CreditLimitAssigned in assignCreditLimit"
```

---

## Task 5: RED — `changeCreditLimit` applies event internally

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`

- [ ] **Step 1: Update the failing test**

Replace the body of `changeCreditLimitHappyPath` with:

```java
@Test
void changeCreditLimitHappyPath() {
    CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
    CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
            new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
            new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
    ));

    var events = account.changeCreditLimit(Money.of("150.00"), Instant.parse("2026-06-01T10:02:00Z"));

    assertThat(events).containsExactly(new CreditLimitChanged(accountId, Money.of("150.00"), Instant.parse("2026-06-01T10:02:00Z")));
    assertThat(account.snapshot().creditLimit()).isEqualTo(Money.of("150.00"));
    assertThat(account.version()).isEqualTo(3L);
}
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.changeCreditLimitHappyPath
```

Expected: FAIL because `creditLimit` is still `100.00` and `version` is `2L`.

---

## Task 6: GREEN — `changeCreditLimit` applies event

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`

- [ ] **Step 1: Refactor `changeCreditLimit`**

Replace the method body with:

```java
public List<CreditAccountEvent> changeCreditLimit(Money newLimit, Instant occurredAt) {
    ensureOpened(); ensureLimitAssigned();
    if (!newLimit.isGreaterThan(Money.zero())) { throw new InvalidCreditLimitException("credit limit must be positive"); }
    Money committed = outstandingBalance.plus(authorizedAmount);
    if (newLimit.isLessThan(committed)) { throw new InvalidCreditLimitException("new limit is lower than committed balance"); }
    return recordThat(new CreditLimitChanged(id, newLimit, occurredAt));
}
```

- [ ] **Step 2: Run the test and confirm GREEN**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.changeCreditLimitHappyPath
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
git commit -m "feat(domain): apply CreditLimitChanged in changeCreditLimit"
```

---

## Task 7: RED — `authorizePurchase` applies event internally

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`

- [ ] **Step 1: Update the failing test**

Replace the body of `authorizesPurchaseWhenAvailableLimitIsEnough` with:

```java
@Test
void authorizesPurchaseWhenAvailableLimitIsEnough() {
    CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
    AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
    CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
            new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
            new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z"))
    ));

    var events = account.authorizePurchase(authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z"));

    assertThat(events).containsExactly(new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z")));
    assertThat(account.snapshot().authorizedAmount()).isEqualTo(Money.of("25.00"));
    assertThat(account.snapshot().authorizations()).containsKey(authorizationId);
    assertThat(account.version()).isEqualTo(3L);
}
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.authorizesPurchaseWhenAvailableLimitIsEnough
```

Expected: FAIL because `authorizedAmount` is `Money.zero()` and `version` is `2L`.

---

## Task 8: GREEN — `authorizePurchase` applies event

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`

- [ ] **Step 1: Refactor `authorizePurchase`**

Replace the method body with:

```java
public List<CreditAccountEvent> authorizePurchase(AuthorizationId authorizationId, Money amount, String merchantName, Instant occurredAt) {
    ensureOpened(); ensureLimitAssigned();
    if (authorizations.containsKey(authorizationId)) { throw new AuthorizationAlreadyExistsException("authorization already exists"); }
    if (!amount.isGreaterThan(Money.zero())) { throw new InvalidMoneyException("purchase amount must be positive"); }
    if (amount.isGreaterThan(availableLimit())) { throw new InsufficientAvailableLimitException("insufficient available limit"); }
    return recordThat(new PurchaseAuthorized(id, authorizationId, amount, merchantName, occurredAt));
}
```

- [ ] **Step 2: Run the test and confirm GREEN**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.authorizesPurchaseWhenAvailableLimitIsEnough
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
git commit -m "feat(domain): apply PurchaseAuthorized in authorizePurchase"
```

---

## Task 9: RED — `capturePurchase` applies event internally

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`

- [ ] **Step 1: Update the failing test**

Replace the body of `captureMovesReservedAmountToOutstandingBalance` with:

```java
@Test
void captureMovesReservedAmountToOutstandingBalance() {
    CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
    AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
    CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
            new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
            new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
            new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z"))
    ));

    var events = account.capturePurchase(authorizationId, Instant.parse("2026-06-01T10:03:00Z"));

    assertThat(events).containsExactly(new PurchaseCaptured(accountId, authorizationId, Money.of("25.00"), Instant.parse("2026-06-01T10:03:00Z")));
    assertThat(account.snapshot().outstandingBalance()).isEqualTo(Money.of("25.00"));
    assertThat(account.snapshot().authorizedAmount()).isEqualTo(Money.zero());
    assertThat(account.version()).isEqualTo(4L);
}
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.captureMovesReservedAmountToOutstandingBalance
```

Expected: FAIL because outstanding is zero and version is `3L`.

---

## Task 10: GREEN — `capturePurchase` applies event

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`

- [ ] **Step 1: Refactor `capturePurchase`**

Replace the method body with:

```java
public List<CreditAccountEvent> capturePurchase(AuthorizationId authorizationId, Instant occurredAt) {
    ensureOpened();
    PurchaseAuthorization authorization = openAuthorization(authorizationId);
    return recordThat(new PurchaseCaptured(id, authorizationId, authorization.amount(), occurredAt));
}
```

- [ ] **Step 2: Run the test and confirm GREEN**

Run:

```bash
./gradlew test --tests com.sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.captureMovesReservedAmountToOutstandingBalance
```

If the path includes a dash, run with the fully qualified class:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.captureMovesReservedAmountToOutstandingBalance"
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
git commit -m "feat(domain): apply PurchaseCaptured in capturePurchase"
```

---

## Task 11: RED — `releasePurchaseAuthorization` applies event internally

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`

- [ ] **Step 1: Update the failing test**

Replace the body of `releaseAuthorizationHappyPath` with:

```java
@Test
void releaseAuthorizationHappyPath() {
    CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
    AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
    CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
            new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
            new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
            new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", Instant.parse("2026-06-01T10:02:00Z"))
    ));

    var events = account.releasePurchaseAuthorization(authorizationId, Instant.parse("2026-06-01T10:03:00Z"));

    assertThat(events).containsExactly(new PurchaseAuthorizationReleased(accountId, authorizationId, Money.of("25.00"), Instant.parse("2026-06-01T10:03:00Z")));
    assertThat(account.snapshot().authorizedAmount()).isEqualTo(Money.zero());
    assertThat(account.snapshot().authorizations().get(authorizationId).status()).isEqualTo(PurchaseAuthorizationStatus.RELEASED);
    assertThat(account.version()).isEqualTo(4L);
}
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.releaseAuthorizationHappyPath"
```

Expected: FAIL because `authorizedAmount` is still `25.00` and version is `3L`.

---

## Task 12: GREEN — `releasePurchaseAuthorization` applies event

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`

- [ ] **Step 1: Refactor `releasePurchaseAuthorization`**

Replace the method body with:

```java
public List<CreditAccountEvent> releasePurchaseAuthorization(AuthorizationId authorizationId, Instant occurredAt) {
    ensureOpened();
    PurchaseAuthorization authorization = openAuthorization(authorizationId);
    return recordThat(new PurchaseAuthorizationReleased(id, authorizationId, authorization.amount(), occurredAt));
}
```

- [ ] **Step 2: Run the test and confirm GREEN**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.releaseAuthorizationHappyPath"
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
git commit -m "feat(domain): apply PurchaseAuthorizationReleased in releasePurchaseAuthorization"
```

---

## Task 13: RED — `receivePayment` applies event internally

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`

- [ ] **Step 1: Update the failing test**

Replace the body of `receivePaymentHappyPath` with:

```java
@Test
void receivePaymentHappyPath() {
    CreditAccountId accountId = CreditAccountId.of(UUID.randomUUID());
    AuthorizationId authorizationId = AuthorizationId.of(UUID.randomUUID());
    CreditAccount account = CreditAccount.rehydrate(accountId, List.of(
            new CreditAccountOpened(accountId, Instant.parse("2026-06-01T10:00:00Z")),
            new CreditLimitAssigned(accountId, Money.of("100.00"), Instant.parse("2026-06-01T10:01:00Z")),
            new PurchaseAuthorized(accountId, authorizationId, Money.of("50.00"), "Store", Instant.parse("2026-06-01T10:02:00Z")),
            new PurchaseCaptured(accountId, authorizationId, Money.of("50.00"), Instant.parse("2026-06-01T10:03:00Z"))
    ));

    var events = account.receivePayment(Money.of("25.00"), Instant.parse("2026-06-01T10:04:00Z"));

    assertThat(events).containsExactly(new PaymentReceived(accountId, Money.of("25.00"), Instant.parse("2026-06-01T10:04:00Z")));
    assertThat(account.snapshot().outstandingBalance()).isEqualTo(Money.of("25.00"));
    assertThat(account.version()).isEqualTo(5L);
}
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.receivePaymentHappyPath"
```

Expected: FAIL because outstanding is `50.00` and version is `4L`.

---

## Task 14: GREEN — `receivePayment` applies event

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`

- [ ] **Step 1: Refactor `receivePayment`**

Replace the method body with:

```java
public List<CreditAccountEvent> receivePayment(Money amount, Instant occurredAt) {
    ensureOpened();
    if (!amount.isGreaterThan(Money.zero())) { throw new InvalidMoneyException("payment amount must be positive"); }
    if (amount.isGreaterThan(outstandingBalance)) { throw new PaymentExceedsOutstandingBalanceException("payment exceeds outstanding balance"); }
    return recordThat(new PaymentReceived(id, amount, occurredAt));
}
```

- [ ] **Step 2: Run the test and confirm GREEN**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest.receivePaymentHappyPath"
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
git commit -m "feat(domain): apply PaymentReceived in receivePayment"
```

---

## Task 15: Refactor rehydrate and remove public `applyAll`

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java`

- [ ] **Step 1: Replace `rehydrate` to use `applyHistorical`**

Replace the existing `rehydrate` method with:

```java
public static CreditAccount rehydrate(CreditAccountId id, List<CreditAccountEvent> history) {
    CreditAccount account = new CreditAccount(id);
    history.forEach(account::applyHistorical);
    return account;
}
```

- [ ] **Step 2: Add the `applyHistorical` private method**

Add this method next to the existing private helpers:

```java
private void applyHistorical(CreditAccountEvent event) {
    apply(event);
    version++;
}
```

- [ ] **Step 3: Remove public `applyAll`**

Delete the entire `public void applyAll(List<CreditAccountEvent> events)` method.

- [ ] **Step 4: Run the full domain test class**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest
```

Expected: PASS for all tests, including rejection tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java
git commit -m "refactor(domain): use applyHistorical and drop public applyAll"
```

---

## Task 16: Adjust `CreditAccountUseCaseSupport`

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`

- [ ] **Step 1: Update the `execute` method**

Replace the current body of the private `execute` method with:

```java
private ExecutionResult execute(String aggregateId, CreditAccountId creditAccountId, CommandExecutor executor, String idempotencyKey, String commandType, String requestHash) {
    List<CreditAccountEvent> history = loadHistory(aggregateId);
    CreditAccount account = CreditAccount.rehydrate(creditAccountId, history);

    long expectedVersion = account.version();
    List<CreditAccountEvent> newEvents = executor.execute(account);

    Map<String, String> metadata = Map.of(
            "idempotencyKey", idempotencyKey,
            "commandType", commandType,
            "requestHash", requestHash
    );
    AppendResult appendResult = eventStore.appendEvents(
            AGGREGATE_TYPE, aggregateId, expectedVersion, newEvents, metadata);

    CreditAccountOutput output = buildOutput(account);
    return new ExecutionResult(output, appendResult.newAggregateVersion(), false);
}
```

- [ ] **Step 2: Run use case tests**

Run:

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.usecase.*"
```

Expected: PASS for every use case test.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java
git commit -m "feat(usecase): capture expectedVersion before command and drop manual apply"
```

---

## Task 17: RED — `AuthorizePurchaseUseCaseTest` asserts expected version

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java`

- [ ] **Step 1: Add an explicit assertion for the pre-command version**

In `executeAuthorizesPurchase`, add the following assertion right after the `output` retrieval:

```java
verify(eventStore).appendEvents(
        eq("CreditAccount"),
        eq(accountId.toString()),
        eq(2L),
        anyList(),
        anyMap()
);
```

- [ ] **Step 2: Run the test and confirm GREEN**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.core.usecase.AuthorizePurchaseUseCaseTest.executeAuthorizesPurchase
```

Expected: PASS. The aggregate has two historical events so the pre-command version is `2L`, and the test confirms the support class passes that to `appendEvents`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java
git commit -m "test(usecase): assert pre-command version passed to appendEvents"
```

---

## Task 18: Update README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add the design decision entry**

In the `Key Design Decisions` bullet list in `README.md`, add a new bullet:

```md
- **Aggregate-applied domain events**: aggregate command methods validate invariants, create domain events, apply them to aggregate state, and return the already-applied events to the application layer for persistence. This keeps events explicit without requiring a pending-events list.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: document aggregate-applied events design"
```

---

## Task 19: Final verification

**Files:**
- Read: `docs/superpowers/specs/2026-06-10-aggregate-applied-events-design.md`
- Read: `docs/superpowers/plans/2026-06-10-aggregate-applied-events.md`

- [ ] **Step 1: Run focused test suite**

Run:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.core.usecase.*"
```

Expected: PASS.

- [ ] **Step 2: Run full test suite**

Run:

```bash
./gradlew test
```

Expected: PASS, or record any environment limitations (for example, Docker unavailable for Testcontainers).

- [ ] **Step 3: Run static analysis checks if configured locally**

If the project runs spotbugs/PMD/checkstyle as part of the build, no extra action is needed; the previous test runs already execute them.

- [ ] **Step 4: Confirm `applyAll` is no longer referenced**

Run:

```bash
grep -R "applyAll" src/main/java src/test/java
```

Expected: no output.

---

## Self-Review Notes

- Every spec acceptance criterion is mapped to a task:
  - command methods apply events before returning → Tasks 2, 4, 6, 8, 10, 12, 14.
  - command methods still return events for persistence → Task 16.
  - support no longer applies events manually → Task 16.
  - `expectedVersion` captured before command → Task 16.
  - public `applyAll` removed → Task 15.
  - domain tests prove state and version → Tasks 1–14.
  - use case tests prove pre-command version → Task 17.
  - README updated → Task 18.
  - relevant Gradle tests pass → Tasks 1–17, 19.
- No `TBD`/`TODO` placeholders in this plan.
- Type and method names consistent across tasks: `recordThat`, `applyHistorical`, `expectedVersion`, `appendEvents` parameters.
