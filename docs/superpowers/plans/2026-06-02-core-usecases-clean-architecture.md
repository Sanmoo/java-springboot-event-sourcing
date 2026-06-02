# Core Use Cases Clean Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the centralized `CreditAccountCommandService` and `application.*Command/Result` with Clean Architecture concrete `UseCase` classes under a `core` package, preserving all external REST behavior.

**Architecture:** Each supported operation becomes a concrete class with a single `execute(Input)` method. Shared orchestration (idempotency, event loading, event appending, snapshot building) lives in `CreditAccountUseCaseSupport`. No use case interfaces.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Gradle Kotlin DSL, JUnit 5, Mockito, AssertJ

---

## File Map

### New files to create
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/AppendResult.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/EventEnvelope.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/EventStorePort.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyDecision.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port/IdempotencyPort.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/ConcurrencyConflictException.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error/IdempotencyConflictException.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/PurchaseAuthorizationOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountInput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCase.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitInput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCase.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitInput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseInput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCase.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseInput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCase.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationInput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCase.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentInput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentOutput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountInput.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountOutput.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupportTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCaseTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCaseTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCaseTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCaseTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCaseTest.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCaseTest.java`

### Files to modify
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestExceptionHandler.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestConfiguration.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`

### Files to remove
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/AssignCreditLimitCommand.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/AuthorizePurchaseCommand.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/CapturePurchaseCommand.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/ChangeCreditLimitCommand.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/OpenCreditAccountCommand.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/ReceivePaymentCommand.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/command/ReleasePurchaseAuthorizationCommand.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/result/CommandResult.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/result/CreditAccountResult.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandService.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/AppendResult.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/EventEnvelope.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/EventStorePort.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/IdempotencyDecision.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port/IdempotencyPort.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/error/ConcurrencyConflictException.java`
- `src/main/java/com/sanmoo/eventsourcing/creditaccount/application/error/IdempotencyConflictException.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java`

---

## Task 1: Move application ports and errors to core

**Goal:** Preserve existing port and error contracts under `core` so later tasks can import from the new package.

- [ ] **Step 1: Move port files**

Run:
```bash
git mv src/main/java/com/sanmoo/eventsourcing/creditaccount/application/port src/main/java/com/sanmoo/eventsourcing/creditaccount/core/port
```

Then update the package declaration in all 5 files under the new `core/port` directory from `package com.sanmoo.eventsourcing.creditaccount.application.port;` to `package com.sanmoo.eventsourcing.creditaccount.core.port;`.

- [ ] **Step 2: Move error files**

Run:
```bash
git mv src/main/java/com/sanmoo/eventsourcing/creditaccount/application/error src/main/java/com/sanmoo/eventsourcing/creditaccount/core/error
```

Then update the package declaration in both files under `core/error` from `package com.sanmoo.eventsourcing.creditaccount.application.error;` to `package com.sanmoo.eventsourcing.creditaccount.core.error;`.

- [ ] **Step 3: Update adapter imports**

Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapter.java`:

Replace:
```java
import com.sanmoo.eventsourcing.creditaccount.application.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.application.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventStorePort;
```

With:
```java
import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
```

Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcIdempotencyAdapter.java`:

Replace:
```java
import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyPort;
```

With:
```java
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
```

- [ ] **Step 4: Update exception handler imports**

Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestExceptionHandler.java`:

Replace:
```java
import com.sanmoo.eventsourcing.creditaccount.application.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.application.error.IdempotencyConflictException;
```

With:
```java
import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
```

- [ ] **Step 5: Verify build compiles**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: move application ports and errors to core package"
```

---

## Task 2: Create shared output DTOs

**Goal:** Create `CreditAccountOutput` and `PurchaseAuthorizationOutput` as shared DTOs for all use case outputs.

- [ ] **Step 1: Create PurchaseAuthorizationOutput**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/PurchaseAuthorizationOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record PurchaseAuthorizationOutput(
        String authorizationId,
        String amount,
        String status,
        String merchantName
) {}
```

- [ ] **Step 2: Create CreditAccountOutput**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.util.List;

public record CreditAccountOutput(
        String creditAccountId,
        boolean opened,
        String creditLimit,
        String outstandingBalance,
        String authorizedAmount,
        String availableLimit,
        List<PurchaseAuthorizationOutput> authorizations
) {}
```

- [ ] **Step 3: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: add shared CreditAccountOutput and PurchaseAuthorizationOutput"
```

---

## Task 3: Create CreditAccountUseCaseSupport

**Goal:** Extract shared idempotency/event-sourcing orchestration into a single support class. It must not contain business-named methods like `receivePayment`.

- [ ] **Step 1: Create the support class**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.CreditAccount;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountSnapshot;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import com.sanmoo.eventsourcing.creditaccount.domain.model.PurchaseAuthorization;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CreditAccountUseCaseSupport {

    private static final String AGGREGATE_TYPE = "CreditAccount";

    private final EventStorePort eventStore;
    private final IdempotencyPort idempotencyPort;
    private final ObjectMapper objectMapper;

    public CreditAccountUseCaseSupport(EventStorePort eventStore, IdempotencyPort idempotencyPort, ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.idempotencyPort = idempotencyPort;
        this.objectMapper = objectMapper;
    }

    public <I, O> O executeIdempotent(
            String idempotencyKey,
            String commandType,
            CreditAccountId creditAccountId,
            I input,
            CommandExecutor executor,
            Function<ExecutionResult, O> outputMapper
    ) {
        String aggregateId = creditAccountId.value().toString();
        String requestHash = calculateRequestHash(input);

        IdempotencyDecision decision = idempotencyPort.start(idempotencyKey, commandType, aggregateId, requestHash);

        return switch (decision) {
            case IdempotencyDecision.Replay replay -> {
                ExecutionResult result = deserializeReplay(replay);
                yield outputMapper.apply(result);
            }
            case IdempotencyDecision.Conflict conflict ->
                    throw new IdempotencyConflictException(conflict.message());
            case IdempotencyDecision.Started started -> {
                ExecutionResult result = execute(aggregateId, creditAccountId, executor);
                String payload = serializeResult(result);
                idempotencyPort.complete(idempotencyKey, payload);
                yield outputMapper.apply(result);
            }
        };
    }

    public CreditAccountOutput loadAccountOutput(CreditAccountId creditAccountId) {
        String aggregateId = creditAccountId.value().toString();
        List<CreditAccountEvent> history = loadHistory(aggregateId);
        CreditAccount account = CreditAccount.rehydrate(creditAccountId, history);
        if (!account.snapshot().opened()) {
            throw new com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotFoundException(
                    "Credit account not found: " + aggregateId);
        }
        return buildOutput(account);
    }

    private ExecutionResult execute(String aggregateId, CreditAccountId creditAccountId, CommandExecutor executor) {
        List<CreditAccountEvent> history = loadHistory(aggregateId);
        CreditAccount account = CreditAccount.rehydrate(creditAccountId, history);

        List<CreditAccountEvent> newEvents = executor.execute(account);
        long expectedVersion = account.version();
        account.applyAll(newEvents);

        AppendResult appendResult = eventStore.appendEvents(
                AGGREGATE_TYPE, aggregateId, expectedVersion, newEvents, Map.of());

        CreditAccountOutput output = buildOutput(account);
        return new ExecutionResult(output, appendResult.newAggregateVersion(), false);
    }

    private List<CreditAccountEvent> loadHistory(String aggregateId) {
        return eventStore.loadEvents(AGGREGATE_TYPE, aggregateId)
                .stream()
                .map(EventEnvelope::event)
                .collect(Collectors.toList());
    }

    private CreditAccountOutput buildOutput(CreditAccount account) {
        CreditAccountSnapshot snapshot = account.snapshot();

        List<PurchaseAuthorizationOutput> authList = snapshot.authorizations().values().stream()
                .map(auth -> new PurchaseAuthorizationOutput(
                        auth.id().value().toString(),
                        auth.amount().amount().toPlainString(),
                        auth.status().name(),
                        auth.merchantName()
                ))
                .collect(Collectors.toList());

        return new CreditAccountOutput(
                snapshot.id().value().toString(),
                snapshot.opened(),
                snapshot.creditLimit() != null ? snapshot.creditLimit().amount().toPlainString() : null,
                snapshot.outstandingBalance().amount().toPlainString(),
                snapshot.authorizedAmount().amount().toPlainString(),
                snapshot.creditLimit() != null
                        ? snapshot.availableLimit().amount().toPlainString()
                        : Money.zero().amount().toPlainString(),
                authList
        );
    }

    private ExecutionResult deserializeReplay(IdempotencyDecision.Replay replay) {
        try {
            Map<String, Object> raw = objectMapper.readValue(replay.responsePayload(), objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            String aggregateId = (String) raw.get("aggregateId");
            long aggregateVersion = ((Number) raw.get("aggregateVersion")).longValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = (Map<String, Object>) raw.get("responseData");
            CreditAccountOutput output = objectMapper.convertValue(responseData, CreditAccountOutput.class);
            return new ExecutionResult(output, aggregateVersion, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize idempotency response payload", e);
        }
    }

    private String serializeResult(ExecutionResult result) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("aggregateId", result.output().creditAccountId());
            payload.put("aggregateVersion", result.aggregateVersion());
            payload.put("responseData", objectMapper.convertValue(result.output(), Map.class));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response for idempotency", e);
        }
    }

    private String calculateRequestHash(Object input) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(input);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(serialized);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request", e);
        }
    }

    @FunctionalInterface
    public interface CommandExecutor {
        List<CreditAccountEvent> execute(CreditAccount account);
    }

    public record ExecutionResult(CreditAccountOutput output, long aggregateVersion, boolean replayed) {}
}
```

- [ ] **Step 2: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: add CreditAccountUseCaseSupport for shared idempotency and event sourcing"
```

---

## Task 4: Create OpenCreditAccount use case

**Goal:** Replace `openCreditAccount` with `OpenCreditAccountUseCase`.

- [ ] **Step 1: Create Input**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountInput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record OpenCreditAccountInput(String idempotencyKey) {}
```

- [ ] **Step 2: Create Output**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record OpenCreditAccountOutput(CreditAccountOutput account, boolean replayed) {}
```

- [ ] **Step 3: Create UseCase**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.CreditAccount;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

import java.time.Instant;

public class OpenCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;

    public OpenCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public OpenCreditAccountOutput execute(OpenCreditAccountInput input) {
        CreditAccountId creditAccountId = CreditAccountId.newId();
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

- [ ] **Step 4: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: add OpenCreditAccountUseCase"
```

---

## Task 5: Create AssignCreditLimit use case

**Goal:** Replace `assignCreditLimit` with `AssignCreditLimitUseCase`.

- [ ] **Step 1: Create Input**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitInput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;

public record AssignCreditLimitInput(String idempotencyKey, CreditAccountId creditAccountId, Money creditLimit) {}
```

- [ ] **Step 2: Create Output**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record AssignCreditLimitOutput(CreditAccountOutput account, boolean replayed) {}
```

- [ ] **Step 3: Create UseCase**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCase.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;

public class AssignCreditLimitUseCase {

    private final CreditAccountUseCaseSupport support;

    public AssignCreditLimitUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public AssignCreditLimitOutput execute(AssignCreditLimitInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "AssignCreditLimit",
                input.creditAccountId(),
                input,
                account -> account.assignCreditLimit(input.creditLimit(), now()),
                result -> new AssignCreditLimitOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
```

- [ ] **Step 4: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: add AssignCreditLimitUseCase"
```

---

## Task 6: Create ChangeCreditLimit use case

**Goal:** Replace `changeCreditLimit` with `ChangeCreditLimitUseCase`.

- [ ] **Step 1: Create Input**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitInput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;

public record ChangeCreditLimitInput(String idempotencyKey, CreditAccountId creditAccountId, Money newCreditLimit) {}
```

- [ ] **Step 2: Create Output**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record ChangeCreditLimitOutput(CreditAccountOutput account, boolean replayed) {}
```

- [ ] **Step 3: Create UseCase**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCase.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;

public class ChangeCreditLimitUseCase {

    private final CreditAccountUseCaseSupport support;

    public ChangeCreditLimitUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public ChangeCreditLimitOutput execute(ChangeCreditLimitInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "ChangeCreditLimit",
                input.creditAccountId(),
                input,
                account -> account.changeCreditLimit(input.newCreditLimit(), now()),
                result -> new ChangeCreditLimitOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
```

- [ ] **Step 4: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: add ChangeCreditLimitUseCase"
```

---

## Task 7: Create AuthorizePurchase use case

**Goal:** Replace `authorizePurchase` with `AuthorizePurchaseUseCase`.

- [ ] **Step 1: Create Input**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseInput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;

public record AuthorizePurchaseInput(
        String idempotencyKey,
        CreditAccountId creditAccountId,
        AuthorizationId authorizationId,
        Money amount,
        String merchantName
) {}
```

- [ ] **Step 2: Create Output**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record AuthorizePurchaseOutput(
        CreditAccountOutput account,
        String authorizationId,
        boolean replayed
) {}
```

- [ ] **Step 3: Create UseCase**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;

public class AuthorizePurchaseUseCase {

    private final CreditAccountUseCaseSupport support;

    public AuthorizePurchaseUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public AuthorizePurchaseOutput execute(AuthorizePurchaseInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "AuthorizePurchase",
                input.creditAccountId(),
                input,
                account -> account.authorizePurchase(
                        input.authorizationId(), input.amount(), input.merchantName(), now()),
                result -> new AuthorizePurchaseOutput(
                        result.output(),
                        input.authorizationId().value().toString(),
                        result.replayed()
                )
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
```

- [ ] **Step 4: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: add AuthorizePurchaseUseCase"
```

---

## Task 8: Create CapturePurchase use case

**Goal:** Replace `capturePurchase` with `CapturePurchaseUseCase`.

- [ ] **Step 1: Create Input**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseInput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

public record CapturePurchaseInput(String idempotencyKey, CreditAccountId creditAccountId, AuthorizationId authorizationId) {}
```

- [ ] **Step 2: Create Output**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record CapturePurchaseOutput(CreditAccountOutput account, boolean replayed) {}
```

- [ ] **Step 3: Create UseCase**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCase.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;

public class CapturePurchaseUseCase {

    private final CreditAccountUseCaseSupport support;

    public CapturePurchaseUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public CapturePurchaseOutput execute(CapturePurchaseInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "CapturePurchase",
                input.creditAccountId(),
                input,
                account -> account.capturePurchase(input.authorizationId(), now()),
                result -> new CapturePurchaseOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
```

- [ ] **Step 4: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: add CapturePurchaseUseCase"
```

---

## Task 9: Create ReleasePurchaseAuthorization use case

**Goal:** Replace `releasePurchaseAuthorization` with `ReleasePurchaseAuthorizationUseCase`.

- [ ] **Step 1: Create Input**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationInput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

public record ReleasePurchaseAuthorizationInput(String idempotencyKey, CreditAccountId creditAccountId, AuthorizationId authorizationId) {}
```

- [ ] **Step 2: Create Output**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record ReleasePurchaseAuthorizationOutput(CreditAccountOutput account, boolean replayed) {}
```

- [ ] **Step 3: Create UseCase**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCase.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;

public class ReleasePurchaseAuthorizationUseCase {

    private final CreditAccountUseCaseSupport support;

    public ReleasePurchaseAuthorizationUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public ReleasePurchaseAuthorizationOutput execute(ReleasePurchaseAuthorizationInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "ReleasePurchaseAuthorization",
                input.creditAccountId(),
                input,
                account -> account.releasePurchaseAuthorization(input.authorizationId(), now()),
                result -> new ReleasePurchaseAuthorizationOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
```

- [ ] **Step 4: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: add ReleasePurchaseAuthorizationUseCase"
```

---

## Task 10: Create ReceivePayment use case

**Goal:** Replace `receivePayment` with `ReceivePaymentUseCase`.

- [ ] **Step 1: Create Input**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentInput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;

public record ReceivePaymentInput(String idempotencyKey, CreditAccountId creditAccountId, Money amount) {}
```

- [ ] **Step 2: Create Output**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record ReceivePaymentOutput(CreditAccountOutput account, boolean replayed) {}
```

- [ ] **Step 3: Create UseCase**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCase.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;

public class ReceivePaymentUseCase {

    private final CreditAccountUseCaseSupport support;

    public ReceivePaymentUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public ReceivePaymentOutput execute(ReceivePaymentInput input) {
        return support.executeIdempotent(
                input.idempotencyKey(),
                "ReceivePayment",
                input.creditAccountId(),
                input,
                account -> account.receivePayment(input.amount(), now()),
                result -> new ReceivePaymentOutput(result.output(), result.replayed())
        );
    }

    private Instant now() {
        return Instant.now();
    }
}
```

- [ ] **Step 4: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: add ReceivePaymentUseCase"
```

---

## Task 11: Create GetCreditAccount use case

**Goal:** Replace `getAccount` with `GetCreditAccountUseCase`.

- [ ] **Step 1: Create Input**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountInput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;

public record GetCreditAccountInput(CreditAccountId creditAccountId) {}
```

- [ ] **Step 2: Create Output**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountOutput.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public record GetCreditAccountOutput(CreditAccountOutput account) {}
```

- [ ] **Step 3: Create UseCase**

Create `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

public class GetCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;

    public GetCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public GetCreditAccountOutput execute(GetCreditAccountInput input) {
        CreditAccountOutput output = support.loadAccountOutput(input.creditAccountId());
        return new GetCreditAccountOutput(output);
    }
}
```

- [ ] **Step 4: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: add GetCreditAccountUseCase"
```

---

## Task 12: Update Spring configuration

**Goal:** Replace the `CreditAccountCommandService` bean with `CreditAccountUseCaseSupport` and all concrete use case beans.

- [ ] **Step 1: Rewrite RestConfiguration**

Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestConfiguration.java` to:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestConfiguration {

    @Bean
    public CreditAccountUseCaseSupport creditAccountUseCaseSupport(
            EventStorePort eventStorePort,
            IdempotencyPort idempotencyPort,
            ObjectMapper objectMapper
    ) {
        return new CreditAccountUseCaseSupport(eventStorePort, idempotencyPort, objectMapper);
    }

    @Bean
    public OpenCreditAccountUseCase openCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        return new OpenCreditAccountUseCase(support);
    }

    @Bean
    public AssignCreditLimitUseCase assignCreditLimitUseCase(CreditAccountUseCaseSupport support) {
        return new AssignCreditLimitUseCase(support);
    }

    @Bean
    public ChangeCreditLimitUseCase changeCreditLimitUseCase(CreditAccountUseCaseSupport support) {
        return new ChangeCreditLimitUseCase(support);
    }

    @Bean
    public AuthorizePurchaseUseCase authorizePurchaseUseCase(CreditAccountUseCaseSupport support) {
        return new AuthorizePurchaseUseCase(support);
    }

    @Bean
    public CapturePurchaseUseCase capturePurchaseUseCase(CreditAccountUseCaseSupport support) {
        return new CapturePurchaseUseCase(support);
    }

    @Bean
    public ReleasePurchaseAuthorizationUseCase releasePurchaseAuthorizationUseCase(CreditAccountUseCaseSupport support) {
        return new ReleasePurchaseAuthorizationUseCase(support);
    }

    @Bean
    public ReceivePaymentUseCase receivePaymentUseCase(CreditAccountUseCaseSupport support) {
        return new ReceivePaymentUseCase(support);
    }

    @Bean
    public GetCreditAccountUseCase getCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        return new GetCreditAccountUseCase(support);
    }
}
```

- [ ] **Step 2: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: rewire Spring configuration for use cases"
```

---

## Task 13: Update REST controller to use use cases

**Goal:** Replace all `CreditAccountCommandService` references with concrete use cases and map inputs/outputs.

- [ ] **Step 1: Rewrite CreditAccountController**

Modify `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.*;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.*;
import com.sanmoo.eventsourcing.creditaccount.domain.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/credit-accounts")
public class CreditAccountController {

    private final OpenCreditAccountUseCase openCreditAccountUseCase;
    private final AssignCreditLimitUseCase assignCreditLimitUseCase;
    private final ChangeCreditLimitUseCase changeCreditLimitUseCase;
    private final AuthorizePurchaseUseCase authorizePurchaseUseCase;
    private final CapturePurchaseUseCase capturePurchaseUseCase;
    private final ReleasePurchaseAuthorizationUseCase releasePurchaseAuthorizationUseCase;
    private final ReceivePaymentUseCase receivePaymentUseCase;
    private final GetCreditAccountUseCase getCreditAccountUseCase;

    public CreditAccountController(
            OpenCreditAccountUseCase openCreditAccountUseCase,
            AssignCreditLimitUseCase assignCreditLimitUseCase,
            ChangeCreditLimitUseCase changeCreditLimitUseCase,
            AuthorizePurchaseUseCase authorizePurchaseUseCase,
            CapturePurchaseUseCase capturePurchaseUseCase,
            ReleasePurchaseAuthorizationUseCase releasePurchaseAuthorizationUseCase,
            ReceivePaymentUseCase receivePaymentUseCase,
            GetCreditAccountUseCase getCreditAccountUseCase
    ) {
        this.openCreditAccountUseCase = openCreditAccountUseCase;
        this.assignCreditLimitUseCase = assignCreditLimitUseCase;
        this.changeCreditLimitUseCase = changeCreditLimitUseCase;
        this.authorizePurchaseUseCase = authorizePurchaseUseCase;
        this.capturePurchaseUseCase = capturePurchaseUseCase;
        this.releasePurchaseAuthorizationUseCase = releasePurchaseAuthorizationUseCase;
        this.receivePaymentUseCase = receivePaymentUseCase;
        this.getCreditAccountUseCase = getCreditAccountUseCase;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> openCreditAccount(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) OpenCreditAccountRequest request) {
        var input = new OpenCreditAccountInput(idempotencyKey);
        var output = openCreditAccountUseCase.execute(input);
        if (output.replayed()) {
            return ResponseEntity.ok(toMap(output.account()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(output.account()));
    }

    @PostMapping("/{id}/credit-limit")
    public ResponseEntity<Map<String, Object>> assignCreditLimit(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AssignCreditLimitRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var input = new AssignCreditLimitInput(idempotencyKey, creditAccountId, Money.positive(request.limit()));
        var output = assignCreditLimitUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    @PostMapping("/{id}/purchases/authorizations")
    public ResponseEntity<Map<String, Object>> authorizePurchase(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizePurchaseRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authorizationId = AuthorizationId.of(UUID.fromString(request.authorizationId()));
        var input = new AuthorizePurchaseInput(
                idempotencyKey, creditAccountId, authorizationId,
                Money.positive(request.amount()), request.merchantName());
        var output = authorizePurchaseUseCase.execute(input);
        Map<String, Object> response = new LinkedHashMap<>(toMap(output.account()));
        response.put("authorizationId", output.authorizationId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/purchases/authorizations/{authorizationId}/capture")
    public ResponseEntity<Map<String, Object>> capturePurchase(
            @PathVariable String id,
            @PathVariable String authorizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) CapturePurchaseRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authId = AuthorizationId.of(UUID.fromString(authorizationId));
        var input = new CapturePurchaseInput(idempotencyKey, creditAccountId, authId);
        var output = capturePurchaseUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    @PostMapping("/{id}/purchases/authorizations/{authorizationId}/release")
    public ResponseEntity<Map<String, Object>> releasePurchaseAuthorization(
            @PathVariable String id,
            @PathVariable String authorizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) ReleasePurchaseAuthorizationRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authId = AuthorizationId.of(UUID.fromString(authorizationId));
        var input = new ReleasePurchaseAuthorizationInput(idempotencyKey, creditAccountId, authId);
        var output = releasePurchaseAuthorizationUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<Map<String, Object>> receivePayment(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReceivePaymentRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var input = new ReceivePaymentInput(idempotencyKey, creditAccountId, Money.positive(request.amount()));
        var output = receivePaymentUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAccount(@PathVariable String id) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var input = new GetCreditAccountInput(creditAccountId);
        var output = getCreditAccountUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    private Map<String, Object> toMap(CreditAccountOutput output) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("creditAccountId", output.creditAccountId());
        data.put("opened", output.opened());
        data.put("creditLimit", output.creditLimit());
        data.put("outstandingBalance", output.outstandingBalance());
        data.put("authorizedAmount", output.authorizedAmount());
        data.put("availableLimit", output.availableLimit());
        data.put("authorizations", output.authorizations().stream()
                .map(auth -> {
                    Map<String, Object> authMap = new LinkedHashMap<>();
                    authMap.put("authorizationId", auth.authorizationId());
                    authMap.put("amount", auth.amount());
                    authMap.put("status", auth.status());
                    authMap.put("merchantName", auth.merchantName());
                    return authMap;
                })
                .toList());
        return data;
    }
}
```

- [ ] **Step 2: Verify build**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: wire REST controller to concrete use cases"
```

---

## Task 14: Remove legacy application layer

**Goal:** Delete the old `application` package completely, including commands, results, service, and now-empty directories.

- [ ] **Step 1: Delete old files**

Run:
```bash
rm -rf src/main/java/com/sanmoo/eventsourcing/creditaccount/application
rm -f src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java
```

- [ ] **Step 2: Verify build**

Run:
```bash
./gradlew compileJava compileTestJava
```

Expected: BUILD SUCCESSFUL. If there are any remaining references to `application.*`, fix them now.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: remove legacy application layer"
```

---

## Task 15: Add OpenCreditAccountUseCaseTest

**Goal:** Verify `OpenCreditAccountUseCase` creates an account and handles idempotency replay.

- [ ] **Step 1: Create test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenCreditAccountUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private OpenCreditAccountUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new OpenCreditAccountUseCase(support);
    }

    @Test
    void executeAppendsCreditAccountOpenedAtExpectedVersionZero() {
        when(idempotencyPort.start(any(), eq("OpenCreditAccount"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of());
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(1L));

        var input = new OpenCreditAccountInput("key-1");
        var output = useCase.execute(input);

        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                anyString(),
                eq(0L),
                argThat(events -> events.size() == 1),
                anyMap()
        );
        assertThat(output.account().creditAccountId()).isNotNull();
        assertThat(output.replayed()).isFalse();
    }

    @Test
    void sameIdempotencyKeyAndHashReturnsReplayedOutput() throws Exception {
        var previousData = new java.util.LinkedHashMap<String, Object>();
        previousData.put("creditAccountId", "550e8400-e29b-41d4-a716-446655440000");
        previousData.put("opened", true);
        previousData.put("creditLimit", null);
        previousData.put("outstandingBalance", "0.00");
        previousData.put("authorizedAmount", "0.00");
        previousData.put("availableLimit", "0.00");
        previousData.put("authorizations", List.of());
        var previousPayload = objectMapper.writeValueAsString(Map.of(
                "aggregateId", "550e8400-e29b-41d4-a716-446655440000",
                "aggregateVersion", 1,
                "responseData", previousData
        ));

        when(idempotencyPort.start(any(), eq("OpenCreditAccount"), any(), any()))
                .thenReturn(new IdempotencyDecision.Replay(previousPayload));

        var input = new OpenCreditAccountInput("key-2");
        var output = useCase.execute(input);

        verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
        assertThat(output.account().creditAccountId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(output.replayed()).isTrue();
    }
}
```

- [ ] **Step 2: Run test**

Run:
```bash
./gradlew test --tests OpenCreditAccountUseCaseTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: add OpenCreditAccountUseCaseTest"
```

---

## Task 16: Add AssignCreditLimitUseCaseTest

**Goal:** Verify limit assignment.

- [ ] **Step 1: Create test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCaseTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignCreditLimitUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private AssignCreditLimitUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new AssignCreditLimitUseCase(support);
    }

    @Test
    void executeAssignsCreditLimitToExistingAccount() {
        UUID accountId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);

        when(idempotencyPort.start(any(), eq("AssignCreditLimit"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(Instant.now()), Instant.now(), Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(2L));

        var input = new AssignCreditLimitInput("key-1", creditAccountId, Money.positive("500.00"));
        var output = useCase.execute(input);

        assertThat(output.account().creditLimit()).isEqualTo("500.00");
        assertThat(output.replayed()).isFalse();
    }
}
```

- [ ] **Step 2: Run test**

Run:
```bash
./gradlew test --tests AssignCreditLimitUseCaseTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: add AssignCreditLimitUseCaseTest"
```

---

## Task 17: Add ChangeCreditLimitUseCaseTest

**Goal:** Verify limit change.

- [ ] **Step 1: Create test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCaseTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChangeCreditLimitUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private ChangeCreditLimitUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new ChangeCreditLimitUseCase(support);
    }

    @Test
    void executeChangesCreditLimit() {
        UUID accountId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        Instant now = Instant.now();

        when(idempotencyPort.start(any(), eq("ChangeCreditLimit"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2,
                        new CreditLimitAssigned(Money.positive("500.00"), now), now, Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(3L));

        var input = new ChangeCreditLimitInput("key-1", creditAccountId, Money.positive("750.00"));
        var output = useCase.execute(input);

        assertThat(output.account().creditLimit()).isEqualTo("750.00");
        assertThat(output.replayed()).isFalse();
    }
}
```

- [ ] **Step 2: Run test**

Run:
```bash
./gradlew test --tests ChangeCreditLimitUseCaseTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: add ChangeCreditLimitUseCaseTest"
```

---

## Task 18: Add AuthorizePurchaseUseCaseTest

**Goal:** Verify purchase authorization.

- [ ] **Step 1: Create test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthorizePurchaseUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private AuthorizePurchaseUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new AuthorizePurchaseUseCase(support);
    }

    @Test
    void executeAuthorizesPurchase() {
        UUID accountId = UUID.randomUUID();
        UUID authId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        AuthorizationId authorizationId = AuthorizationId.of(authId);
        Instant now = Instant.now();

        when(idempotencyPort.start(any(), eq("AuthorizePurchase"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2,
                        new CreditLimitAssigned(Money.positive("500.00"), now), now, Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(3L));

        var input = new AuthorizePurchaseInput("key-1", creditAccountId, authorizationId, Money.positive("100.00"), "Store");
        var output = useCase.execute(input);

        assertThat(output.account().authorizedAmount()).isEqualTo("100.00");
        assertThat(output.authorizationId()).isEqualTo(authId.toString());
        assertThat(output.replayed()).isFalse();
    }
}
```

- [ ] **Step 2: Run test**

Run:
```bash
./gradlew test --tests AuthorizePurchaseUseCaseTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: add AuthorizePurchaseUseCaseTest"
```

---

## Task 19: Add CapturePurchaseUseCaseTest

**Goal:** Verify purchase capture.

- [ ] **Step 1: Create test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCaseTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorized;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CapturePurchaseUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private CapturePurchaseUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new CapturePurchaseUseCase(support);
    }

    @Test
    void executeCapturesPurchase() {
        UUID accountId = UUID.randomUUID();
        UUID authId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        AuthorizationId authorizationId = AuthorizationId.of(authId);
        Instant now = Instant.now();

        when(idempotencyPort.start(any(), eq("CapturePurchase"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2,
                        new CreditLimitAssigned(Money.positive("500.00"), now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3,
                        new PurchaseAuthorized(authorizationId, Money.positive("100.00"), "Store", now), now, Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(4L));

        var input = new CapturePurchaseInput("key-1", creditAccountId, authorizationId);
        var output = useCase.execute(input);

        assertThat(output.account().outstandingBalance()).isEqualTo("100.00");
        assertThat(output.replayed()).isFalse();
    }
}
```

- [ ] **Step 2: Run test**

Run:
```bash
./gradlew test --tests CapturePurchaseUseCaseTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: add CapturePurchaseUseCaseTest"
```

---

## Task 20: Add ReleasePurchaseAuthorizationUseCaseTest

**Goal:** Verify authorization release.

- [ ] **Step 1: Create test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCaseTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorized;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReleasePurchaseAuthorizationUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private ReleasePurchaseAuthorizationUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new ReleasePurchaseAuthorizationUseCase(support);
    }

    @Test
    void executeReleasesAuthorization() {
        UUID accountId = UUID.randomUUID();
        UUID authId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        AuthorizationId authorizationId = AuthorizationId.of(authId);
        Instant now = Instant.now();

        when(idempotencyPort.start(any(), eq("ReleasePurchaseAuthorization"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2,
                        new CreditLimitAssigned(Money.positive("500.00"), now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3,
                        new PurchaseAuthorized(authorizationId, Money.positive("100.00"), "Store", now), now, Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(4L));

        var input = new ReleasePurchaseAuthorizationInput("key-1", creditAccountId, authorizationId);
        var output = useCase.execute(input);

        assertThat(output.account().authorizedAmount()).isEqualTo("0.00");
        assertThat(output.replayed()).isFalse();
    }
}
```

- [ ] **Step 2: Run test**

Run:
```bash
./gradlew test --tests ReleasePurchaseAuthorizationUseCaseTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: add ReleasePurchaseAuthorizationUseCaseTest"
```

---

## Task 21: Add ReceivePaymentUseCaseTest

**Goal:** Verify payment reception.

- [ ] **Step 1: Create test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCaseTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorized;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCaptured;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReceivePaymentUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private ReceivePaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new ReceivePaymentUseCase(support);
    }

    @Test
    void executeReceivesPayment() {
        UUID accountId = UUID.randomUUID();
        UUID authId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        AuthorizationId authorizationId = AuthorizationId.of(authId);
        Instant now = Instant.now();

        when(idempotencyPort.start(any(), eq("ReceivePayment"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2,
                        new CreditLimitAssigned(Money.positive("500.00"), now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 3,
                        new PurchaseAuthorized(authorizationId, Money.positive("100.00"), "Store", now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 4,
                        new PurchaseCaptured(authorizationId, now), now, Map.of())
        ));
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(5L));

        var input = new ReceivePaymentInput("key-1", creditAccountId, Money.positive("50.00"));
        var output = useCase.execute(input);

        assertThat(output.account().outstandingBalance()).isEqualTo("50.00");
        assertThat(output.replayed()).isFalse();
    }
}
```

- [ ] **Step 2: Run test**

Run:
```bash
./gradlew test --tests ReceivePaymentUseCaseTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: add ReceivePaymentUseCaseTest"
```

---

## Task 22: Add GetCreditAccountUseCaseTest

**Goal:** Verify account loading for queries.

- [ ] **Step 1: Create test file**

Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCaseTest.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetCreditAccountUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private GetCreditAccountUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        useCase = new GetCreditAccountUseCase(support);
    }

    @Test
    void executeReturnsAccountState() {
        UUID accountId = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(accountId);
        Instant now = Instant.now();

        when(eventStore.loadEvents(any(), any())).thenReturn(List.of(
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 1,
                        new CreditAccountOpened(now), now, Map.of()),
                new EventEnvelope(UUID.randomUUID(), "CreditAccount", accountId.toString(), 2,
                        new CreditLimitAssigned(Money.positive("500.00"), now), now, Map.of())
        ));

        var input = new GetCreditAccountInput(creditAccountId);
        var output = useCase.execute(input);

        assertThat(output.account().creditAccountId()).isEqualTo(accountId.toString());
        assertThat(output.account().opened()).isTrue();
        assertThat(output.account().creditLimit()).isEqualTo("500.00");
    }
}
```

- [ ] **Step 2: Run test**

Run:
```bash
./gradlew test --tests GetCreditAccountUseCaseTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: add GetCreditAccountUseCaseTest"
```

---

## Task 23: Full test verification

**Goal:** Ensure all tests pass and external behavior is preserved.

- [ ] **Step 1: Run full test suite**

Run:
```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit if any final adjustments were needed**

If no changes were necessary after the full test run, skip. If fixes were applied:

```bash
git add -A
git commit -m "fix: resolve remaining test/incompatibility issues after refactoring"
```

---

## Self-Review

### 1. Spec coverage

- Move application ports/errors to core: Task 1
- Shared outputs: Task 2
- CreditAccountUseCaseSupport: Task 3
- Each concrete use case with Input/Output: Tasks 4-11
- Spring configuration update: Task 12
- REST controller update: Task 13
- Legacy application removal: Task 14
- One test per productive class: Tasks 15-22
- Full verification: Task 23

All sections of the spec are mapped.

### 2. Placeholder scan

- No TBD, TODO, or vague instructions found.
- All steps include exact file paths and complete code blocks.
- All steps include exact commands with expected outputs.

### 3. Type consistency

- `CreditAccountUseCaseSupport.executeIdempotent` signature is used consistently across all use cases.
- `ExecutionResult` fields (`output`, `aggregateVersion`, `replayed`) are used uniformly.
- `CreditAccountOutput` and `PurchaseAuthorizationOutput` are reused across outputs.
- Controller `toMap` method maps the same fields previously returned by `buildResponseData`.
