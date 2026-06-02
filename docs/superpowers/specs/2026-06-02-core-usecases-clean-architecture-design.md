# Core Use Cases Clean Architecture Design

## Context

The project currently centralizes application behavior in `CreditAccountCommandService` and models application inputs as `application.command.*Command`. This naming does not express the business use cases clearly. The desired direction is Clean Architecture with screaming architecture: the core package should make the supported use cases obvious by name.

This refactoring changes architecture and naming without changing external REST behavior.

## Goals

- Replace `application` with `core` as the package name for the business/application core.
- Remove `CreditAccountCommandService`.
- Remove `application.command.*Command` and `application.result.*Result`.
- Introduce clearly named concrete use case classes ending in `UseCase`.
- Use one public method per use case: `execute(Input input): Output`.
- Avoid use case interfaces for now.
- Preserve current REST endpoints, HTTP statuses, JSON fields, idempotency behavior, event persistence, and domain behavior.
- Keep one test class per productive class.

## Target Package Structure

```text
core/
  error/
    ConcurrencyConflictException
    IdempotencyConflictException

  port/
    AppendResult
    EventEnvelope
    EventStorePort
    IdempotencyDecision
    IdempotencyPort

  usecase/
    OpenCreditAccountUseCase
    OpenCreditAccountInput
    OpenCreditAccountOutput

    AssignCreditLimitUseCase
    AssignCreditLimitInput
    AssignCreditLimitOutput

    ChangeCreditLimitUseCase
    ChangeCreditLimitInput
    ChangeCreditLimitOutput

    AuthorizePurchaseUseCase
    AuthorizePurchaseInput
    AuthorizePurchaseOutput

    CapturePurchaseUseCase
    CapturePurchaseInput
    CapturePurchaseOutput

    ReleasePurchaseAuthorizationUseCase
    ReleasePurchaseAuthorizationInput
    ReleasePurchaseAuthorizationOutput

    ReceivePaymentUseCase
    ReceivePaymentInput
    ReceivePaymentOutput

    GetCreditAccountUseCase
    GetCreditAccountInput
    GetCreditAccountOutput

    CreditAccountOutput
    PurchaseAuthorizationOutput
    CreditAccountUseCaseSupport
```

The existing `adapter` and `domain` packages remain conceptually unchanged. Adapters will import ports and errors from `core` instead of `application`.

## Use Cases, Inputs, and Outputs

Each use case is a concrete class with a single standard public method:

```java
public ReceivePaymentOutput execute(ReceivePaymentInput input)
```

Inputs are case-specific records and replace the old `*Command` records. For example:

```java
public record ReceivePaymentInput(
        String idempotencyKey,
        CreditAccountId creditAccountId,
        Money amount
) {}
```

Outputs use a hybrid model:

- each use case has a specific `*Output` record;
- shared account state is represented by `CreditAccountOutput`;
- operation-specific fields remain in the specific output.

For example:

```java
public record ReceivePaymentOutput(CreditAccountOutput account, boolean replayed) {}

public record AuthorizePurchaseOutput(
        CreditAccountOutput account,
        String authorizationId,
        boolean replayed
) {}
```

`CreditAccountOutput` represents the shared account view currently returned as a map:

- `creditAccountId`
- `opened`
- `creditLimit`
- `outstandingBalance`
- `authorizedAmount`
- `availableLimit`
- `authorizations`

`PurchaseAuthorizationOutput` represents each authorization in that shared account output.

## Shared Use Case Support

`CreditAccountUseCaseSupport` centralizes technical orchestration common to multiple use cases. It must not expose business-named methods such as `receivePayment` or `authorizePurchase`; those names belong in the concrete use case classes.

Responsibilities:

- calculate the idempotency request hash;
- start idempotency through `IdempotencyPort`;
- handle replay and conflict decisions;
- load events from `EventStorePort`;
- rehydrate `CreditAccount`;
- execute a domain function supplied by the use case;
- append new events with optimistic locking;
- complete idempotency records;
- build `CreditAccountOutput`;
- support account loading for `GetCreditAccountUseCase`.

Conceptually:

```java
public ReceivePaymentOutput execute(ReceivePaymentInput input) {
    return support.executeIdempotent(
            input.idempotencyKey(),
            "ReceivePayment",
            input.creditAccountId(),
            input,
            account -> account.receivePayment(input.amount(), now()),
            result -> new ReceivePaymentOutput(result.account(), result.replayed())
    );
}
```

This keeps each use case explicit while avoiding duplicate idempotency and event-sourcing plumbing.

## REST and Spring Configuration

The REST API remains externally compatible.

`CreditAccountController` will depend on the concrete use cases instead of `CreditAccountCommandService`:

- `OpenCreditAccountUseCase`
- `AssignCreditLimitUseCase`
- `AuthorizePurchaseUseCase`
- `CapturePurchaseUseCase`
- `ReleasePurchaseAuthorizationUseCase`
- `ReceivePaymentUseCase`
- `GetCreditAccountUseCase`

Each controller method will:

1. read path variables, headers, and request body;
2. build the corresponding `*Input`;
3. call `useCase.execute(input)`;
4. convert the `*Output` into the existing HTTP response shape.

Spring configuration will create `CreditAccountUseCaseSupport` and each concrete use case. Output adapters keep the same behavior and update imports from `application.port.*` to `core.port.*`.

Expected compatibility:

- same endpoints;
- same HTTP statuses;
- same JSON field names;
- same idempotency semantics;
- same event store behavior;
- same domain errors and application/core errors, with package names updated.

## Testing Strategy

Tests should follow the rule: one test class per productive class.

`CreditAccountCommandServiceTest` will be removed or replaced by tests for the new productive classes. Use case tests should be separate, for example:

- `OpenCreditAccountUseCaseTest`
- `AssignCreditLimitUseCaseTest`
- `ChangeCreditLimitUseCaseTest`
- `AuthorizePurchaseUseCaseTest`
- `CapturePurchaseUseCaseTest`
- `ReleasePurchaseAuthorizationUseCaseTest`
- `ReceivePaymentUseCaseTest`
- `GetCreditAccountUseCaseTest`
- `CreditAccountUseCaseSupportTest`, if its behavior is directly relevant to test in isolation

REST and integration tests should continue to verify that external behavior did not change.

## Migration Plan Outline

The implementation plan should follow this sequence:

1. Create the `core` package structure.
2. Move `application.port` to `core.port` and `application.error` to `core.error`.
3. Replace `*Command` records with `*Input` records.
4. Replace `*Result` records with `*Output` records.
5. Create `CreditAccountUseCaseSupport`.
6. Create each concrete use case.
7. Update REST controller and Spring configuration.
8. Update adapters and tests to import `core.*`.
9. Remove `CreditAccountCommandService` and old `application` packages.
10. Run the full test suite to verify behavior.

## Non-Goals

- No REST API redesign.
- No domain model redesign.
- No database schema change.
- No read model or projection work.
- No use case interfaces unless a future need appears.
