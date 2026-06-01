# Credit Account Event Sourcing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot practice project for a revolving credit account implemented with manual event sourcing, PostgreSQL, Liquibase, idempotent commands, and REST adapters.

**Architecture:** Use ports and adapters. Keep the domain free of Spring, HTTP, JSON, and persistence concerns. Persist aggregate event sourcing events in PostgreSQL with optimistic locking using `aggregate_*` terminology.

**Tech Stack:** Java latest LTS via Spring Initializr, Spring Boot latest stable via Spring Initializr, Gradle Kotlin DSL, PostgreSQL, Liquibase, JUnit 5, AssertJ, Testcontainers.

---

## File Structure

Create the project with this package root:

```text
com.sanmoo.eventsourcing.creditaccount
```

Planned files:

```text
settings.gradle.kts
build.gradle.kts
src/main/resources/application.yml
src/main/resources/db/changelog/db.changelog-master.yaml
src/main/resources/db/changelog/001-create-event-store.yaml
src/main/resources/db/changelog/002-create-idempotency-records.yaml
src/main/java/com/sanmoo/eventsourcing/creditaccount/CreditAccountApplication.java

src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/Money.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/CreditAccountId.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/AuthorizationId.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/PurchaseAuthorization.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/PurchaseAuthorizationStatus.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/model/CreditAccountSnapshot.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/event/CreditAccountEvent.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/event/CreditAccountOpened.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/event/CreditLimitAssigned.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/event/CreditLimitChanged.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/event/PurchaseAuthorized.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/event/PurchaseCaptured.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/event/PurchaseAuthorizationReleased.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/event/PaymentReceived.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/DomainException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/AccountAlreadyExistsException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/AccountNotFoundException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/CreditLimitAlreadyAssignedException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/CreditLimitNotAssignedException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/InsufficientAvailableLimitException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/InvalidMoneyException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/InvalidCreditLimitException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/AuthorizationAlreadyExistsException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/AuthorizationNotFoundException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/AuthorizationNotOpenException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/error/PaymentExceedsOutstandingBalanceException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccount.java

src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/EventStorePort.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/EventEnvelope.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/AppendResult.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/IdempotencyPort.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/IdempotencyDecision.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/IdempotencyOutcome.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/OpenCreditAccountCommand.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/AssignCreditLimitCommand.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/ChangeCreditLimitCommand.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/AuthorizePurchaseCommand.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/CapturePurchaseCommand.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/ReleasePurchaseAuthorizationCommand.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/ReceivePaymentCommand.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/result/CreditAccountResult.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/result/CommandResult.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandService.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/error/ConcurrencyConflictException.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/error/IdempotencyConflictException.java

src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/EventTypeMapper.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestExceptionHandler.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/*.java

src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java
src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java
src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapterIT.java
src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerIT.java
```

## Task 1: Scaffold the Spring Boot project

**Files:**
- Create: Gradle/Spring project files and base application class.

- [ ] **Step 1: Generate project using latest stable Spring Initializr defaults**

Run from repository root:

```bash
curl -s "https://start.spring.io/starter.zip?type=gradle-project-kotlin&language=java&groupId=com.sanmoo.eventsourcing&artifactId=credit-account-event-sourcing&name=credit-account-event-sourcing&packageName=com.sanmoo.eventsourcing.creditaccount&dependencies=web,validation,jdbc,postgresql,liquibase,testcontainers" -o /tmp/credit-account-event-sourcing.zip
unzip -o /tmp/credit-account-event-sourcing.zip -d /tmp/credit-account-event-sourcing-generated
cp -R /tmp/credit-account-event-sourcing-generated/* .
```

Expected: `build.gradle.kts`, `settings.gradle.kts`, `src/main/java/.../CreditAccountEventSourcingApplication.java` exist.

- [ ] **Step 2: Rename application class to match package convention**

Create/replace `src/main/java/com/sanmoo/eventsourcing/creditaccount/CreditAccountApplication.java`:

```java
package com.sanmoo.eventsourcing.creditaccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CreditAccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditAccountApplication.class, args);
    }
}
```

Remove generated `*Application.java` if its name differs.

- [ ] **Step 3: Add AssertJ explicitly if missing**

Modify `build.gradle.kts` dependencies to include:

```kotlin
testImplementation("org.assertj:assertj-core")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:postgresql")
```

Keep generated Spring Boot and dependency management versions from Spring Initializr.

- [ ] **Step 4: Verify scaffold builds**

Run:

```bash
./gradlew test
```

Expected: build succeeds.

- [ ] **Step 5: Commit scaffold**

```bash
git add .
git commit -m "chore: scaffold Spring Boot project"
```

## Task 2: Add domain value objects and event types

**Files:**
- Create: all files under `src/main/java/.../domain/model`, `domain/event`, and `domain/error` listed above.
- Test: `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`

- [ ] **Step 1: Write failing value object tests**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.domain;

import com.sanmoo.eventsourcing.creditaccount.domain.error.InvalidMoneyException;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditAccountTest {

    @Test
    void moneyRejectsZeroAndNegativeAmounts() {
        assertThatThrownBy(() -> Money.of("0.00"))
                .isInstanceOf(InvalidMoneyException.class);

        assertThatThrownBy(() -> Money.of("-1.00"))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void moneySupportsArithmetic() {
        assertThat(Money.of("10.00").plus(Money.of("2.50"))).isEqualTo(Money.of("12.50"));
        assertThat(Money.of("10.00").minus(Money.of("2.50"))).isEqualTo(Money.of("7.50"));
        assertThat(Money.of("10.00").isGreaterThan(Money.of("9.99"))).isTrue();
        assertThat(Money.zero().amount()).isEqualByComparingTo(new BigDecimal("0.00"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests '*CreditAccountTest'
```

Expected: compilation fails because `Money` and errors do not exist.

- [ ] **Step 3: Implement domain errors**

Create `DomainException` and subclasses as simple runtime exceptions. Example for each file:

```java
package com.sanmoo.eventsourcing.creditaccount.domain.error;

public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }
}
```

Example subclass:

```java
package com.sanmoo.eventsourcing.creditaccount.domain.error;

public final class InvalidMoneyException extends DomainException {
    public InvalidMoneyException(String message) {
        super(message);
    }
}
```

Create equivalent subclasses for all domain errors named in the file structure.

- [ ] **Step 4: Implement value objects**

Create `Money.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.domain.model;

import com.sanmoo.eventsourcing.creditaccount.domain.error.InvalidMoneyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(String amount) {
        return positive(new BigDecimal(amount));
    }

    public static Money positive(BigDecimal amount) {
        Money money = new Money(amount);
        if (money.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidMoneyException("amount must be positive");
        }
        return money;
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(Money other) {
        return this.amount.compareTo(other.amount);
    }
}
```

Create IDs:

```java
package com.sanmoo.eventsourcing.creditaccount.domain.model;

import java.util.UUID;

public record CreditAccountId(UUID value) {
    public static CreditAccountId newId() { return new CreditAccountId(UUID.randomUUID()); }
    public static CreditAccountId of(UUID value) { return new CreditAccountId(value); }
}
```

```java
package com.sanmoo.eventsourcing.creditaccount.domain.model;

import java.util.UUID;

public record AuthorizationId(UUID value) {
    public static AuthorizationId newId() { return new AuthorizationId(UUID.randomUUID()); }
    public static AuthorizationId of(UUID value) { return new AuthorizationId(value); }
}
```

- [ ] **Step 5: Implement event records**

Create sealed interface:

```java
package com.sanmoo.eventsourcing.creditaccount.domain.event;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

import java.time.Instant;

public sealed interface CreditAccountEvent permits CreditAccountOpened, CreditLimitAssigned, CreditLimitChanged, PurchaseAuthorized, PurchaseCaptured, PurchaseAuthorizationReleased, PaymentReceived {
    CreditAccountId creditAccountId();
    Instant occurredAt();
}
```

Create records matching these shapes:

```java
public record CreditAccountOpened(CreditAccountId creditAccountId, Instant occurredAt) implements CreditAccountEvent {}
public record CreditLimitAssigned(CreditAccountId creditAccountId, Money limit, Instant occurredAt) implements CreditAccountEvent {}
public record CreditLimitChanged(CreditAccountId creditAccountId, Money newLimit, Instant occurredAt) implements CreditAccountEvent {}
public record PurchaseAuthorized(CreditAccountId creditAccountId, AuthorizationId authorizationId, Money amount, String merchantName, Instant occurredAt) implements CreditAccountEvent {}
public record PurchaseCaptured(CreditAccountId creditAccountId, AuthorizationId authorizationId, Money amount, Instant occurredAt) implements CreditAccountEvent {}
public record PurchaseAuthorizationReleased(CreditAccountId creditAccountId, AuthorizationId authorizationId, Money amount, Instant occurredAt) implements CreditAccountEvent {}
public record PaymentReceived(CreditAccountId creditAccountId, Money amount, Instant occurredAt) implements CreditAccountEvent {}
```

Add correct package/imports to each file.

- [ ] **Step 6: Run tests**

```bash
./gradlew test --tests '*CreditAccountTest'
```

Expected: PASS.

- [ ] **Step 7: Commit domain primitives**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
git commit -m "feat: add credit account domain primitives"
```

## Task 3: Implement event-sourced aggregate behavior

**Files:**
- Create/modify: `domain/CreditAccount.java`, domain model files, domain tests.

- [ ] **Step 1: Extend tests with Given-When-Then scenarios**

Append tests to `CreditAccountTest.java` for:

```java
@Test
void opensCreditAccount() {
    CreditAccountId accountId = CreditAccountId.newId();

    CreditAccount account = CreditAccount.rehydrate(accountId, java.util.List.of());
    var events = account.open(java.time.Instant.parse("2026-06-01T10:00:00Z"));

    assertThat(events).containsExactly(new CreditAccountOpened(accountId, java.time.Instant.parse("2026-06-01T10:00:00Z")));
}

@Test
void authorizesPurchaseWhenAvailableLimitIsEnough() {
    CreditAccountId accountId = CreditAccountId.newId();
    AuthorizationId authorizationId = AuthorizationId.newId();
    CreditAccount account = CreditAccount.rehydrate(accountId, java.util.List.of(
            new CreditAccountOpened(accountId, java.time.Instant.parse("2026-06-01T10:00:00Z")),
            new CreditLimitAssigned(accountId, Money.of("100.00"), java.time.Instant.parse("2026-06-01T10:01:00Z"))
    ));

    var events = account.authorizePurchase(authorizationId, Money.of("25.00"), "Book Store", java.time.Instant.parse("2026-06-01T10:02:00Z"));

    assertThat(events).containsExactly(new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", java.time.Instant.parse("2026-06-01T10:02:00Z")));
}

@Test
void rejectsPurchaseWhenAvailableLimitIsInsufficient() {
    CreditAccountId accountId = CreditAccountId.newId();
    CreditAccount account = CreditAccount.rehydrate(accountId, java.util.List.of(
            new CreditAccountOpened(accountId, java.time.Instant.parse("2026-06-01T10:00:00Z")),
            new CreditLimitAssigned(accountId, Money.of("100.00"), java.time.Instant.parse("2026-06-01T10:01:00Z"))
    ));

    assertThatThrownBy(() -> account.authorizePurchase(AuthorizationId.newId(), Money.of("101.00"), "Book Store", java.time.Instant.now()))
            .isInstanceOf(InsufficientAvailableLimitException.class);
}

@Test
void captureMovesReservedAmountToOutstandingBalance() {
    CreditAccountId accountId = CreditAccountId.newId();
    AuthorizationId authorizationId = AuthorizationId.newId();
    CreditAccount account = CreditAccount.rehydrate(accountId, java.util.List.of(
            new CreditAccountOpened(accountId, java.time.Instant.parse("2026-06-01T10:00:00Z")),
            new CreditLimitAssigned(accountId, Money.of("100.00"), java.time.Instant.parse("2026-06-01T10:01:00Z")),
            new PurchaseAuthorized(accountId, authorizationId, Money.of("25.00"), "Book Store", java.time.Instant.parse("2026-06-01T10:02:00Z"))
    ));

    var events = account.capturePurchase(authorizationId, java.time.Instant.parse("2026-06-01T10:03:00Z"));

    assertThat(events).containsExactly(new PurchaseCaptured(accountId, authorizationId, Money.of("25.00"), java.time.Instant.parse("2026-06-01T10:03:00Z")));
}
```

Add imports for domain events, IDs, Money, and exceptions.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests '*CreditAccountTest'
```

Expected: compilation fails because aggregate methods do not exist.

- [ ] **Step 3: Implement authorization model and snapshot**

Create:

```java
package com.sanmoo.eventsourcing.creditaccount.domain.model;

public enum PurchaseAuthorizationStatus {
    OPEN,
    CAPTURED,
    RELEASED
}
```

```java
package com.sanmoo.eventsourcing.creditaccount.domain.model;

public record PurchaseAuthorization(AuthorizationId id, Money amount, PurchaseAuthorizationStatus status, String merchantName) {
    public PurchaseAuthorization capture() {
        return new PurchaseAuthorization(id, amount, PurchaseAuthorizationStatus.CAPTURED, merchantName);
    }

    public PurchaseAuthorization release() {
        return new PurchaseAuthorization(id, amount, PurchaseAuthorizationStatus.RELEASED, merchantName);
    }
}
```

```java
package com.sanmoo.eventsourcing.creditaccount.domain.model;

import java.util.Map;

public record CreditAccountSnapshot(
        CreditAccountId id,
        boolean opened,
        Money creditLimit,
        Money outstandingBalance,
        Money authorizedAmount,
        Money availableLimit,
        Map<AuthorizationId, PurchaseAuthorization> authorizations
) {}
```

- [ ] **Step 4: Implement aggregate**

Create `CreditAccount.java` with methods:

```java
package com.sanmoo.eventsourcing.creditaccount.domain;

import com.sanmoo.eventsourcing.creditaccount.domain.error.*;
import com.sanmoo.eventsourcing.creditaccount.domain.event.*;
import com.sanmoo.eventsourcing.creditaccount.domain.model.*;

import java.time.Instant;
import java.util.*;

public final class CreditAccount {
    private final CreditAccountId id;
    private boolean opened;
    private Money creditLimit;
    private Money outstandingBalance = Money.zero();
    private Money authorizedAmount = Money.zero();
    private final Map<AuthorizationId, PurchaseAuthorization> authorizations = new LinkedHashMap<>();
    private long version;

    private CreditAccount(CreditAccountId id) { this.id = id; }

    public static CreditAccount rehydrate(CreditAccountId id, List<CreditAccountEvent> history) {
        CreditAccount account = new CreditAccount(id);
        history.forEach(event -> { account.apply(event); account.version++; });
        return account;
    }

    public List<CreditAccountEvent> open(Instant occurredAt) {
        if (opened) throw new AccountAlreadyExistsException("credit account already exists");
        return List.of(new CreditAccountOpened(id, occurredAt));
    }

    public List<CreditAccountEvent> assignCreditLimit(Money limit, Instant occurredAt) {
        ensureOpened();
        if (creditLimit != null) throw new CreditLimitAlreadyAssignedException("credit limit already assigned");
        return List.of(new CreditLimitAssigned(id, limit, occurredAt));
    }

    public List<CreditAccountEvent> changeCreditLimit(Money newLimit, Instant occurredAt) {
        ensureOpened(); ensureLimitAssigned();
        Money committed = outstandingBalance.plus(authorizedAmount);
        if (newLimit.isLessThan(committed)) throw new InvalidCreditLimitException("new limit is lower than committed balance");
        return List.of(new CreditLimitChanged(id, newLimit, occurredAt));
    }

    public List<CreditAccountEvent> authorizePurchase(AuthorizationId authorizationId, Money amount, String merchantName, Instant occurredAt) {
        ensureOpened(); ensureLimitAssigned();
        if (authorizations.containsKey(authorizationId)) throw new AuthorizationAlreadyExistsException("authorization already exists");
        if (amount.isGreaterThan(availableLimit())) throw new InsufficientAvailableLimitException("insufficient available limit");
        return List.of(new PurchaseAuthorized(id, authorizationId, amount, merchantName, occurredAt));
    }

    public List<CreditAccountEvent> capturePurchase(AuthorizationId authorizationId, Instant occurredAt) {
        ensureOpened();
        PurchaseAuthorization authorization = openAuthorization(authorizationId);
        return List.of(new PurchaseCaptured(id, authorizationId, authorization.amount(), occurredAt));
    }

    public List<CreditAccountEvent> releasePurchaseAuthorization(AuthorizationId authorizationId, Instant occurredAt) {
        ensureOpened();
        PurchaseAuthorization authorization = openAuthorization(authorizationId);
        return List.of(new PurchaseAuthorizationReleased(id, authorizationId, authorization.amount(), occurredAt));
    }

    public List<CreditAccountEvent> receivePayment(Money amount, Instant occurredAt) {
        ensureOpened();
        if (amount.isGreaterThan(outstandingBalance)) throw new PaymentExceedsOutstandingBalanceException("payment exceeds outstanding balance");
        return List.of(new PaymentReceived(id, amount, occurredAt));
    }

    public CreditAccountSnapshot snapshot() {
        return new CreditAccountSnapshot(id, opened, creditLimit, outstandingBalance, authorizedAmount, availableLimit(), Map.copyOf(authorizations));
    }

    public long version() { return version; }

    private Money availableLimit() {
        if (creditLimit == null) return Money.zero();
        return creditLimit.minus(outstandingBalance).minus(authorizedAmount);
    }

    private PurchaseAuthorization openAuthorization(AuthorizationId authorizationId) {
        PurchaseAuthorization authorization = authorizations.get(authorizationId);
        if (authorization == null) throw new AuthorizationNotFoundException("authorization not found");
        if (authorization.status() != PurchaseAuthorizationStatus.OPEN) throw new AuthorizationNotOpenException("authorization is not open");
        return authorization;
    }

    private void ensureOpened() { if (!opened) throw new AccountNotFoundException("credit account not found"); }
    private void ensureLimitAssigned() { if (creditLimit == null) throw new CreditLimitNotAssignedException("credit limit not assigned"); }

    private void apply(CreditAccountEvent event) {
        switch (event) {
            case CreditAccountOpened ignored -> opened = true;
            case CreditLimitAssigned e -> creditLimit = e.limit();
            case CreditLimitChanged e -> creditLimit = e.newLimit();
            case PurchaseAuthorized e -> {
                authorizations.put(e.authorizationId(), new PurchaseAuthorization(e.authorizationId(), e.amount(), PurchaseAuthorizationStatus.OPEN, e.merchantName()));
                authorizedAmount = authorizedAmount.plus(e.amount());
            }
            case PurchaseCaptured e -> {
                PurchaseAuthorization authorization = authorizations.get(e.authorizationId());
                authorizations.put(e.authorizationId(), authorization.capture());
                authorizedAmount = authorizedAmount.minus(e.amount());
                outstandingBalance = outstandingBalance.plus(e.amount());
            }
            case PurchaseAuthorizationReleased e -> {
                PurchaseAuthorization authorization = authorizations.get(e.authorizationId());
                authorizations.put(e.authorizationId(), authorization.release());
                authorizedAmount = authorizedAmount.minus(e.amount());
            }
            case PaymentReceived e -> outstandingBalance = outstandingBalance.minus(e.amount());
        }
    }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew test --tests '*CreditAccountTest'
```

Expected: PASS.

- [ ] **Step 6: Add remaining domain tests**

Add tests for:

- cannot assign limit before open;
- cannot assign limit twice;
- cannot reduce limit below outstanding plus authorized;
- cannot capture missing authorization;
- cannot release captured authorization;
- cannot pay more than outstanding.

Use the same Given-When-Then style and assert exact exception classes.

- [ ] **Step 7: Run all domain tests and commit**

```bash
./gradlew test --tests '*CreditAccountTest'
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/domain src/test/java/com/sanmoo/eventsourcing/creditaccount/domain/CreditAccountTest.java
git commit -m "feat: implement credit account aggregate"
```

## Task 4: Add Liquibase schema for event store and idempotency

**Files:**
- Create: `src/main/resources/application.yml`
- Create: Liquibase changelog files.
- Test: database integration tests in later tasks.

- [ ] **Step 1: Create application config**

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: credit-account-event-sourcing
  datasource:
    url: jdbc:postgresql://localhost:5432/credit_account
    username: credit_account
    password: credit_account
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

- [ ] **Step 2: Create master changelog**

Create `src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/001-create-event-store.yaml
  - include:
      file: db/changelog/002-create-idempotency-records.yaml
```

- [ ] **Step 3: Create event store changelog**

Create `001-create-event-store.yaml` with `event_store`, unique `(aggregate_id, aggregate_version)`, and index on `(aggregate_type, aggregate_id)`.

- [ ] **Step 4: Create idempotency changelog**

Create `002-create-idempotency-records.yaml` with columns from the spec and primary key `idempotency_key`.

- [ ] **Step 5: Commit migrations**

```bash
git add src/main/resources/application.yml src/main/resources/db/changelog
git commit -m "feat: add database migrations"
```

## Task 5: Implement PostgreSQL event store adapter

**Files:**
- Create: application port files.
- Create: postgres adapter files.
- Test: `JdbcEventStoreAdapterIT.java`.

- [ ] **Step 1: Write failing integration tests**

Create `JdbcEventStoreAdapterIT.java` using Testcontainers PostgreSQL, `@SpringBootTest`, and `@DynamicPropertySource`. Test:

- append then load returns same event type and data;
- duplicate aggregate version throws `ConcurrencyConflictException`.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests '*JdbcEventStoreAdapterIT'
```

Expected: compilation fails because ports/adapter do not exist.

- [ ] **Step 3: Implement application port records**

Create:

```java
public record EventEnvelope(UUID eventId, String aggregateType, String aggregateId, long aggregateVersion, CreditAccountEvent event, Instant occurredAt, Map<String, String> metadata) {}
public record AppendResult(long newAggregateVersion) {}
```

Create `EventStorePort` with:

```java
List<EventEnvelope> loadEvents(String aggregateType, String aggregateId);
AppendResult appendEvents(String aggregateType, String aggregateId, long expectedVersion, List<CreditAccountEvent> events, Map<String, String> metadata);
```

- [ ] **Step 4: Implement event type mapper**

Map each event class to its simple event type string and back. Use Jackson `ObjectMapper` in the adapter, not in domain.

- [ ] **Step 5: Implement JDBC adapter**

Use `JdbcTemplate` and a transaction. Insert events with versions `expectedVersion + 1 ... expectedVersion + events.size()`. Translate duplicate key into `ConcurrencyConflictException`.

- [ ] **Step 6: Run integration tests and commit**

```bash
./gradlew test --tests '*JdbcEventStoreAdapterIT'
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java
git commit -m "feat: add PostgreSQL event store adapter"
```

## Task 6: Implement idempotency adapter

**Files:**
- Create: `IdempotencyPort`, `IdempotencyDecision`, `IdempotencyOutcome`, `JdbcIdempotencyAdapter`.
- Test: `JdbcIdempotencyAdapterIT.java`.

- [ ] **Step 1: Write failing idempotency integration tests**

Test:

- first key reserves processing;
- repeated key with same hash returns completed result;
- repeated key with different hash throws/returns conflict.

- [ ] **Step 2: Implement idempotency port**

Use decisions:

```java
public sealed interface IdempotencyDecision permits IdempotencyDecision.Started, IdempotencyDecision.Replay, IdempotencyDecision.Conflict {
    record Started(String key) implements IdempotencyDecision {}
    record Replay(String responsePayload) implements IdempotencyDecision {}
    record Conflict(String message) implements IdempotencyDecision {}
}
```

Port methods:

```java
IdempotencyDecision start(String key, String commandType, String aggregateId, String requestHash);
void complete(String key, String responsePayload);
```

- [ ] **Step 3: Implement JDBC adapter**

Use `INSERT` for new keys and `SELECT` for existing keys. Return conflict when `request_hash` differs.

- [ ] **Step 4: Run tests and commit**

```bash
./gradlew test --tests '*JdbcIdempotencyAdapterIT'
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapterIT.java
git commit -m "feat: add idempotency store adapter"
```

## Task 7: Implement application command service

**Files:**
- Create: command records, result records, `CreditAccountCommandService`.
- Test: `CreditAccountCommandServiceTest.java`.

- [ ] **Step 1: Write failing application service tests with fake ports**

Test one happy path and one idempotent replay:

- `openCreditAccount` appends `CreditAccountOpened` at expected version 0.
- same idempotency key and hash returns previous response without appending.

- [ ] **Step 2: Implement command records**

Create records for each command with fields:

```java
String idempotencyKey;
CreditAccountId creditAccountId;
Money amountOrLimit;
AuthorizationId authorizationId;
String merchantName;
```

Use exact records per command from the file structure.

- [ ] **Step 3: Implement result records**

Create `CommandResult(String aggregateId, long aggregateVersion)` and `CreditAccountResult` containing snapshot fields.

- [ ] **Step 4: Implement `CreditAccountCommandService`**

For each use case:

1. Calculate deterministic request hash from command fields.
2. Start idempotency.
3. If replay, deserialize/return previous response.
4. If conflict, throw `IdempotencyConflictException`.
5. Load aggregate events.
6. Rehydrate aggregate.
7. Call aggregate method.
8. Append new events with `account.version()`.
9. Complete idempotency with response JSON.

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew test --tests '*CreditAccountCommandServiceTest'
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/application src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java
git commit -m "feat: add credit account command service"
```

## Task 8: Implement REST adapter

**Files:**
- Create: `CreditAccountController`, REST DTOs, `RestExceptionHandler`.
- Test: `CreditAccountControllerIT.java`.

- [ ] **Step 1: Write failing REST integration test**

Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` and Testcontainers. Test the full happy path:

1. `POST /credit-accounts`
2. `POST /credit-accounts/{id}/credit-limit`
3. `POST /credit-accounts/{id}/purchases/authorizations`
4. `POST /credit-accounts/{id}/purchases/authorizations/{authorizationId}/capture`
5. `POST /credit-accounts/{id}/payments`
6. `GET /credit-accounts/{id}`

Assert final available limit and outstanding balance.

- [ ] **Step 2: Implement DTOs**

Create request DTOs with `BigDecimal` amounts and response DTOs with string IDs and amounts.

- [ ] **Step 3: Implement controller**

Read `Idempotency-Key` from headers for every POST. Convert DTOs to command records. Return `201 Created` for open account, `200 OK` for other commands and GET.

- [ ] **Step 4: Implement exception handler**

Map exceptions as specified:

- account exists/idempotency/concurrency conflicts to 409;
- account not found to 404;
- domain validation errors to 422.

- [ ] **Step 5: Run REST tests and commit**

```bash
./gradlew test --tests '*CreditAccountControllerIT'
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerIT.java
git commit -m "feat: add credit account REST API"
```

## Task 9: Final verification and documentation cleanup

**Files:**
- Modify: `README.md`.

- [ ] **Step 1: Create README**

Document:

- Project purpose.
- Stack.
- Architecture summary.
- REST endpoint list.
- How to run tests.

- [ ] **Step 2: Run full verification**

```bash
./gradlew clean test
```

Expected: all unit and integration tests pass.

- [ ] **Step 3: Check git status**

```bash
git status --short
```

Expected: only README changes before final commit.

- [ ] **Step 4: Commit README**

```bash
git add README.md
git commit -m "docs: describe credit account event sourcing project"
```

## Self-Review Notes

Spec coverage:

- Ports and adapters: covered by tasks 5-8.
- Domain event sourcing: covered by tasks 2-3.
- PostgreSQL/Liquibase event store: covered by tasks 4-5.
- Aggregate terminology: enforced in event store schema and port naming.
- Idempotency: covered by tasks 6-7 and REST header behavior in task 8.
- REST first, messaging later: REST implemented; outbox/messaging intentionally deferred.
- Read model deferred: GET rehydrates aggregate through service.
- Outbox future: documented in design, not implemented in this plan.
- Tests: domain, application, PostgreSQL integration, and REST integration covered.

No placeholders are intentionally left for implementation behavior. Deferred items are explicitly outside MVP scope per the approved design.
