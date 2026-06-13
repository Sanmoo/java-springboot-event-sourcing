# Projection Quality Gateway and Core Spring Boundary Design

Date: 2026-06-12

## Context

The production-like outbox/projection pipeline introduced explicit delivery state, projection checkpoints, stale delivery recovery, retry/backoff behavior, and same-aggregate draining. After the feature was merged into `main`, the full quality gateway exposed two problems:

1. `core.projection` depends directly on Spring transaction infrastructure through `PlatformTransactionManager` and `TransactionTemplate`.
2. PIT mutation testing fails below the configured threshold. The project already failed before this feature at 71% versus the 80% threshold, but the new projection code lowered the score to 56% because `core.projection` has insufficient behavioral test coverage.

The project already has the intended transaction abstraction:

- Core port: `com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner`
- Spring adapter: `com.sanmoo.eventsourcing.creditaccount.adapter.out.transaction.SpringTransactionRunner`

The projection code should depend on the port, not Spring transaction APIs.

## Goals

1. Remove Spring transaction infrastructure dependencies from `core.projection`.
2. Add enough behavioral tests for the projection pipeline so `./gradlew check` passes, including PIT with `mutationThreshold = 80`.
3. Strengthen ArchUnit rules so future Spring infrastructure leaks into `core` are caught automatically.
4. Preserve the current production-like outbox/projection behavior.

## Non-Goals

1. Do not make the whole core 100% Spring-free in this change.
2. Do not remove existing allowed Spring annotations such as component discovery or transactional annotations from use cases.
3. Do not reduce the PIT mutation threshold.
4. Do not exclude `core.projection` broadly from PIT.
5. Do not expose outbox, deliveries, checkpoints, or projection internals in business-facing acceptance scenarios.

## Architecture

Use selective hardening of the core boundary.

`ProjectionWorker` and `StaleDeliveryRecovery` must replace direct Spring transaction dependencies:

```java
PlatformTransactionManager
TransactionTemplate
```

with the core port:

```java
TransactionRunner
```

The Spring-specific transaction implementation remains in the adapter layer:

```java
adapter.out.transaction.SpringTransactionRunner
```

This preserves the current hexagonal direction:

```text
core.projection --> core.port.TransactionRunner <-- adapter.out.transaction.SpringTransactionRunner
```

The core continues to orchestrate projection behavior, but transaction execution is delegated through a port.

## Component Changes

### ProjectionWorker

`ProjectionWorker` should depend on `TransactionRunner` instead of `PlatformTransactionManager`.

Existing transaction scopes must be preserved:

1. Claim pending deliveries in a transaction.
2. Process each delivery in a transaction.
3. If the processing transaction throws, record retryable or permanent failure outside that failed transaction.

The behavioral semantics must not change:

- empty claim returns an empty result;
- claimed deliveries are counted;
- `BLOCKED` marks the delivery blocked without incrementing technical retry attempts;
- `PERMANENT_FAILURE` marks the delivery failed;
- `ALREADY_APPLIED` marks the delivery processed;
- `APPLY` updates the summary, checkpoint, delivery status, and attempts to unblock the next version;
- retry backoff remains capped by `maxBackoff`.

### StaleDeliveryRecovery

`StaleDeliveryRecovery` should depend on `TransactionRunner` instead of `PlatformTransactionManager`.

The recovery operation must still run transactionally:

```java
transactionRunner.runInTransaction(() ->
    deliveries.recoverStaleProcessing(properties.getProcessingTimeout(), 100)
);
```

### SpringTransactionRunner

`SpringTransactionRunner` remains the concrete adapter that uses Spring's `TransactionTemplate`.

No new port method is required unless implementation reveals a clear need. The current method is sufficient:

```java
<T> T runInTransaction(Supplier<T> action)
```

## ArchUnit Rules

Strengthen `ArchitectureFitnessFunctions` so `core` may only depend on explicitly allowed Spring packages.

Allowed Spring dependencies in `core` for this spec:

- `org.springframework.stereotype..`
- `org.springframework.transaction.annotation..`

Everything else under `org.springframework..` should be disallowed from `core`, including:

- `org.springframework.transaction.PlatformTransactionManager`
- `org.springframework.transaction.support.TransactionTemplate`
- `org.springframework.jdbc..`
- `org.springframework.web..`
- `org.springframework.boot..`

The rule should explain that framework integration belongs behind ports/adapters. If future work needs another Spring package in `core`, the allow-list must be changed deliberately and justified in that diff.

## Test Strategy

### Unit and Mutation Tests

Add behavioral tests based on the PIT report, prioritizing mutation survivors and uncovered lines rather than guessing.

Target areas:

1. `ProjectionWorker`
   - empty claim;
   - claim count;
   - missing outbox event goes through retry/permanent failure handling;
   - `BLOCKED` delivery;
   - `ALREADY_APPLIED` delivery;
   - `PERMANENT_FAILURE` delivery;
   - successful `APPLY` updates summary, checkpoint, delivery, and unblocks next version;
   - retry backoff is capped by `maxBackoff`;
   - transaction runner wraps claim and per-delivery processing.

2. `StaleDeliveryRecovery`
   - invokes repository recovery through `TransactionRunner`;
   - passes configured timeout;
   - returns recovered row count.

3. `ProjectionWorkerResult`
   - counters accumulate correctly;
   - result objects remain immutable.

4. `ProjectionGatingResult`
   - factory methods return the expected decision and reason.

5. `ProjectionGating` and `CreditAccountSummaryProjector`
   - add tests only where PIT identifies surviving or uncovered behavior relevant to the projection pipeline.

### ArchUnit Tests

`./gradlew qualityTest` must fail if `core` imports or depends on `TransactionTemplate` or `PlatformTransactionManager` again.

### Acceptance Tests

Acceptance tests remain business-facing. They should not mention outbox, delivery rows, checkpoints, retries, or projection internals.

## Quality Gateway

The implementation is complete only when these commands pass:

```bash
./gradlew check
./gradlew acceptanceTest
```

`./gradlew check` already includes the main verification stack:

- unit tests;
- Checkstyle;
- PMD;
- SpotBugs;
- Error Prone;
- ArchUnit through `qualityTest`;
- PIT mutation testing.

PIT requirements:

- keep `mutationThreshold = 80`;
- do not broadly exclude `core.projection`;
- only allow narrow exclusions for proven equivalent or noisy mutations, and document any such exclusion in the implementation notes.

## Risks and Mitigations

### PIT may require more tests than expected

Mitigation: use the generated PIT report to target actual survivors and uncovered lines. Prioritize `ProjectionWorker`, then `ProjectionGating`, `CreditAccountSummaryProjector`, result records, and stale recovery.

### ArchUnit may remain too permissive

Mitigation: use an explicit Spring allow-list for `core` rather than a broad `org.springframework..` allowance.

### Transaction boundaries may change accidentally

Mitigation: tests should verify that claim, processing, and recovery execute through `TransactionRunner`, while failure handling still happens after a failed processing transaction.

## Acceptance Criteria

1. `ProjectionWorker` and `StaleDeliveryRecovery` no longer import Spring transaction infrastructure.
2. Spring transaction infrastructure remains isolated in adapter/configuration code.
3. ArchUnit blocks non-allow-listed Spring dependencies from `core`.
4. PIT passes with `mutationThreshold = 80` without broad exclusion of `core.projection`.
5. `./gradlew check` passes.
6. `./gradlew acceptanceTest` passes.
7. Business-facing acceptance scenarios remain free of outbox/projection implementation details.
