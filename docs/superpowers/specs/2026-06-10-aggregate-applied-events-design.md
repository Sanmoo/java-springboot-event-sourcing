# Aggregate-Applied Domain Events Design

## Context

The `CreditAccount` aggregate is event-sourced. Today its command methods validate invariants and return new domain events, but they do not apply those events to the aggregate state. The application service currently performs the missing state transition:

```java
List<CreditAccountEvent> newEvents = executor.execute(account);
long expectedVersion = account.version();
account.applyAll(newEvents);
eventStore.appendEvents(...);
```

This works, but it leaves an important domain rule outside the aggregate: a caller can invoke a domain command, receive valid events, and forget to apply them. For a study project focused on event sourcing, the aggregate should better demonstrate that state changes happen through events and that the aggregate owns the rule-to-event-to-state transition.

## Decision

Adopt a hybrid event-sourcing aggregate pattern:

> Aggregate command methods validate invariants, create domain events, apply those events to the aggregate state, and return the already-applied events to the application layer for persistence.

This keeps events explicit in the application layer while avoiding an internal `pendingEvents` list.

## Goals

- Make `CreditAccount` command methods apply their own events.
- Preserve explicit event persistence in `CreditAccountUseCaseSupport`.
- Avoid adding transient `pendingEvents` state to the aggregate.
- Remove public manual event application from callers.
- Keep optimistic locking semantics correct.
- Update tests to prove both returned events and state mutation.
- Document the pattern as a deliberate design decision.

## Non-Goals

- Introduce command objects for every domain operation.
- Add a `pendingEvents` / `pullPendingEvents()` mechanism.
- Change event-store schema or event serialization.
- Change REST API contracts.
- Change idempotency semantics beyond the internal expected-version ordering.
- Add read models or projections.

## Affected Components

### `CreditAccount`

Affected command methods:

- `open`
- `assignCreditLimit`
- `changeCreditLimit`
- `authorizePurchase`
- `capturePurchase`
- `releasePurchaseAuthorization`
- `receivePayment`

Each method will continue returning `List<CreditAccountEvent>`, but the returned events will already have been applied to the aggregate.

A private helper should centralize new-event recording:

```java
private List<CreditAccountEvent> recordThat(CreditAccountEvent event) {
    apply(event);
    version++;
    return List.of(event);
}
```

If future commands need to emit more than one event, a list overload can be added:

```java
private List<CreditAccountEvent> recordThat(List<CreditAccountEvent> events) {
    events.forEach(event -> {
        apply(event);
        version++;
    });
    return List.copyOf(events);
}
```

The current implementation only needs the single-event helper because every existing command emits exactly one event.

### Rehydration

Historical replay must remain separate from new event recording. Rehydration should reconstruct state and version from persisted history without treating historical events as newly emitted events.

The intended structure is:

```java
public static CreditAccount rehydrate(CreditAccountId id, List<CreditAccountEvent> history) {
    CreditAccount account = new CreditAccount(id);
    history.forEach(account::applyHistorical);
    return account;
}

private void applyHistorical(CreditAccountEvent event) {
    apply(event);
    version++;
}
```

This is behaviorally equivalent to the current replay loop, but the naming makes the distinction explicit.

### Public `applyAll`

The public `applyAll(List<CreditAccountEvent>)` method should be removed.

After command methods apply their own events, a public manual application method becomes dangerous because callers could duplicate state transitions. Historical replay should use a private method instead.

### `CreditAccountUseCaseSupport`

The command execution flow must capture `expectedVersion` before invoking the domain command:

```java
long expectedVersion = account.version();
List<CreditAccountEvent> newEvents = executor.execute(account);
```

The support class must stop calling `account.applyAll(newEvents)` because the aggregate command has already applied the events.

The append call remains responsible for persistence:

```java
eventStore.appendEvents(AGGREGATE_TYPE, aggregateId, expectedVersion, newEvents, metadata);
```

The output should still be built from the mutated aggregate after command execution.

## Data Flow

### Command path after the change

1. Use case receives input.
2. `CreditAccountUseCaseSupport` loads event history.
3. `CreditAccount.rehydrate(...)` rebuilds aggregate state and version from history.
4. Support captures `expectedVersion` from the rehydrated aggregate.
5. Use case invokes a domain command through `CommandExecutor`.
6. Domain command validates invariants.
7. Domain command creates a domain event.
8. Domain command applies the event to aggregate state and increments version.
9. Domain command returns the applied event.
10. Support appends returned events to the event store using the pre-command `expectedVersion`.
11. Support builds output from the post-command aggregate state.
12. Support stores idempotency result.

### Replay/idempotency path

Existing idempotency replay behavior remains unchanged:

1. Lock idempotency key.
2. Find existing record.
3. Verify request hash.
4. Deserialize stored response.
5. Verify replay version.
6. Return replayed output without executing the domain command or appending events.

## Versioning and Optimistic Locking

The important ordering rule is:

```java
long expectedVersion = account.version();
List<CreditAccountEvent> newEvents = executor.execute(account);
```

`expectedVersion` represents the version known before new events. Since command methods now increment `account.version()`, capturing it after command execution would pass the wrong expected version to the event store.

For example, if an account has two historical events:

- before command: `version == 2`
- after one new event: `version == 3`
- append must use `expectedVersion == 2`
- append result should report new aggregate version `3`

## Testing Strategy

Implementation should follow TDD.

### Domain tests

Domain tests must verify both returned events and applied state.

Examples:

- `open` returns `CreditAccountOpened`, marks account as opened, increments version.
- `assignCreditLimit` returns `CreditLimitAssigned`, updates limit, increments version.
- `changeCreditLimit` returns `CreditLimitChanged`, updates limit, increments version.
- `authorizePurchase` returns `PurchaseAuthorized`, reserves authorized amount, adds authorization, increments version.
- `capturePurchase` returns `PurchaseCaptured`, moves authorized amount to outstanding balance, increments version.
- `releasePurchaseAuthorization` returns `PurchaseAuthorizationReleased`, releases reserved amount, increments version.
- `receivePayment` returns `PaymentReceived`, reduces outstanding balance, increments version.

Existing rejection tests should continue proving that invalid commands throw and do not emit events.

### Use case tests

Use case tests should verify that:

- output reflects post-command state even though support no longer calls `applyAll`;
- `eventStore.appendEvents(...)` receives the pre-command expected version;
- idempotency replay does not append events;
- returned aggregate version still comes from `AppendResult`.

### Full verification

Run focused tests first, then the broader suite as practical:

```bash
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.domain.CreditAccountTest
./gradlew test --tests com.sanmoo.eventsourcing.creditaccount.core.usecase.*UseCaseTest
./gradlew test
```

If the full suite requires Docker/Testcontainers and the environment cannot run it, record that limitation explicitly.

## Documentation Updates

Update `README.md` under `Key Design Decisions`:

```md
- **Aggregate-applied domain events**: aggregate command methods validate invariants, create domain events, apply them to aggregate state, and return the already-applied events to the application layer for persistence. This keeps events explicit without requiring a pending-events list.
```

## Risks and Mitigations

### Risk: duplicate event application

If `CreditAccountUseCaseSupport` continues calling `applyAll`, state changes will be applied twice.

Mitigation: remove public `applyAll` and update support flow.

### Risk: wrong optimistic locking version

If `expectedVersion` is captured after command execution, append will use the post-command version.

Mitigation: add/adjust use case verification for expected version passed to `appendEvents`.

### Risk: tests only verify event return

Old domain tests may pass while failing to prove the new contract.

Mitigation: update tests to assert aggregate snapshot and version after command execution.

### Risk: mixing historical and new event semantics

Historical replay and new command execution both call `apply`, but mean different things.

Mitigation: use separate helpers: `applyHistorical` for replay and `recordThat` for new events.

## Acceptance Criteria

- All `CreditAccount` command methods apply their emitted events before returning.
- Command methods still return the emitted events for persistence.
- `CreditAccountUseCaseSupport` no longer applies events manually.
- `expectedVersion` is captured before executing the domain command.
- Public `applyAll` is removed or no longer available to application callers.
- Domain tests prove returned events, state mutation, and version increments.
- Use case tests prove append uses the pre-command expected version.
- README documents the aggregate-applied event design decision.
- Relevant Gradle tests pass, or any environment limitation is documented.
