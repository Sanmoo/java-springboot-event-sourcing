# Transactional Idempotency Replay Design

## Context

The current idempotent command flow stores a durable `STARTED` idempotency record before command execution and later updates it to `COMPLETED` after appending events:

```text
idempotencyPort.start(...)
execute command and append events
idempotencyPort.complete(...)
```

This leaves a failure window: if events are committed successfully but the process crashes before `complete`, the idempotency key remains stuck in `STARTED`. A retry with the same key then returns `idempotency key already in progress`, even though the command may already have produced durable events.

The project already uses PostgreSQL for both the event store and idempotency store. The goal is to make command execution and idempotency replay production-realistic while keeping the scope focused on synchronous command handling.

## Goals

- Remove the durable incomplete `STARTED` state from the idempotency model.
- Ensure event append and idempotency result persistence commit or roll back together.
- Serialize concurrent requests with the same `Idempotency-Key`.
- Keep retries replayable from a stored response payload.
- Preserve the ports-and-adapters architecture.
- Add event metadata that links persisted events back to the command/idempotency key that caused them.

## Non-goals

- Implement an outbox relay or asynchronous inbox/outbox flow.
- Build a full command recovery workflow from event metadata.
- Add read models or projections.
- Support cross-database transactions.
- Make idempotency decisions outside PostgreSQL.

## Recommended Approach

Use a single PostgreSQL transaction around the whole idempotent command flow and use `pg_advisory_xact_lock` to serialize each idempotency key while that transaction is active.

The idempotency table should represent only completed, replayable command results:

```text
record exists    => command completed and can be replayed
record not found => no completed result exists for this key
```

`STARTED` and `COMPLETED` statuses are no longer part of the durable model. The temporary in-progress protection is handled by a transaction-scoped advisory lock, not by a persisted status row.

## Data Model

Refine `idempotency_records` to store only completed command results:

```text
idempotency_key   primary key
command_type      not null
aggregate_id      nullable/current behavior preserved
request_hash      not null
response_payload  not null
aggregate_version not null
created_at        not null, defaults to now
```

Remove or stop depending on:

```text
status
completed_at
```

`created_at` becomes the completion/persistence time for the replayable record. The implementation should add a forward-only Liquibase changeset that removes incomplete historical rows with `response_payload IS NULL`, drops unused status columns, adds `aggregate_version`, and makes `response_payload` and `aggregate_version` non-null. This is acceptable for the current practice project because there is no production data migration requirement.

## Application Flow

`CreditAccountUseCaseSupport.executeIdempotent(...)` remains the orchestration point because it already coordinates hashing, idempotency, aggregate loading, domain execution, event append, response serialization, and output mapping.

The target flow is:

```text
@Transactional
executeIdempotent(...):
  requestHash = hash(input)

  idempotencyPort.lockKey(idempotencyKey)

  existing = idempotencyPort.findByKey(idempotencyKey)

  if existing exists and requestHash matches:
    deserialize existing.responsePayload
    return replayed output

  if existing exists and requestHash differs:
    throw idempotency conflict

  load aggregate history
  execute domain command
  append new events with idempotency metadata
  serialize response payload
  idempotencyPort.saveResult(...)
  return output
```

The transaction must include:

- advisory lock acquisition;
- replay/conflict lookup;
- aggregate history loading;
- domain command execution;
- event append;
- idempotency result insert.

`executeIdempotent(...)` should be invoked through a Spring proxy so `@Transactional` is active. In the current architecture this means keeping it on an injected Spring service rather than moving the transactional method to a private helper or relying on self-invocation.

If the process crashes before commit, PostgreSQL rolls back both event rows and the idempotency result, and releases the advisory lock. If the process crashes after commit, both event rows and replay payload are durable.

## Advisory Lock Design

Use PostgreSQL transaction-scoped advisory locks:

```sql
SELECT pg_advisory_xact_lock(?);
```

The lock key is derived deterministically from the idempotency key string. A Java implementation can hash a namespaced key with SHA-256 and take the first 64 bits as a signed `long`:

```java
sha256("credit-account-idempotency:" + idempotencyKey) -> first 8 bytes -> long
```

The namespace prefix avoids accidental reuse with other future advisory lock domains. The lock lasts until the current transaction commits or rolls back.

Two concurrent requests with the same idempotency key behave as follows:

1. Request A acquires the advisory transaction lock.
2. Request B blocks while trying to acquire the same lock.
3. Request A appends events, saves the idempotency result, and commits.
4. PostgreSQL releases A's lock.
5. Request B acquires the lock, reads the completed result, and returns replay.

Two different idempotency keys can run concurrently unless their 64-bit lock hashes collide. A collision would only over-serialize unrelated keys; correctness remains protected by lookup on the real `idempotency_key` primary key.

## Timeout Behavior

Plain `pg_advisory_xact_lock` can wait indefinitely. The implementation should prefer a bounded wait to avoid tying up request threads forever.

Use one of these PostgreSQL patterns inside the transaction before acquiring the lock:

```sql
SET LOCAL lock_timeout = '5s';
SELECT pg_advisory_xact_lock(?);
```

Use `5s` as the initial timeout. Making this value configurable can be deferred until the project has runtime configuration needs beyond the current spike.

If the lock cannot be acquired before the timeout, throw `IdempotencyConflictException` with a clear message such as `idempotency key is currently being processed`. This preserves the existing REST conflict behavior while avoiding indefinite request-thread blocking.

## Event Metadata

When appending events caused by an idempotent command, include metadata:

```text
idempotencyKey
commandType
requestHash
```

This metadata is not the primary replay mechanism. Replay comes from `idempotency_records.response_payload`. Metadata provides traceability and future recovery options if the replay table ever needs repair.

## Port Shape

Replace the current status-oriented API:

```java
start(...)
complete(...)
```

with a result-oriented API:

```java
void lockKey(String key);
Optional<IdempotencyRecord> findByKey(String key);
void saveResult(String key, String commandType, String aggregateId, String requestHash, String responsePayload, long aggregateVersion);
```

`IdempotencyRecord` should carry at least:

```text
idempotencyKey
commandType
aggregateId
requestHash
responsePayload
aggregateVersion
```

The core layer decides replay vs conflict by comparing the stored `requestHash` with the incoming hash.

## Error Handling

- Existing key with same request hash: replay stored response.
- Existing key with different request hash: throw `IdempotencyConflictException` with the existing reused-key message.
- Lock timeout: throw `IdempotencyConflictException` with an in-progress/timeout message.
- Duplicate insert on `saveResult`: treat as an unexpected concurrency bug because the advisory lock should prevent it for the same key.
- Serialization/deserialization failure: keep current runtime failure behavior; this indicates corrupted or incompatible stored replay payload.

## Testing Plan

Implementation should be test-driven.

Recommended tests:

1. Core use case test proving a completed idempotency record with the same request hash returns replay without executing the command.
2. Core use case test proving a completed idempotency record with a different request hash throws conflict.
3. Core use case test proving new execution appends events with idempotency metadata and saves a replayable result.
4. PostgreSQL adapter integration test proving `lockKey` serializes concurrent transactions with the same key.
5. PostgreSQL adapter integration test proving different keys do not block each other beyond normal scheduling.
6. Integration test proving event append and idempotency result are committed together for successful execution.
7. Integration test or focused transactional test proving rollback leaves neither new events nor idempotency result.

## Acceptance Criteria

- There is no durable `STARTED` idempotency state used by command execution.
- `response_payload` is stored only for completed, replayable commands.
- Event append and idempotency result insert run in the same PostgreSQL transaction.
- Concurrent requests with the same idempotency key are serialized.
- A retry after successful commit returns the stored replay response.
- A retry after rollback can execute normally because no partial idempotency record remains.
- Events caused by idempotent commands include idempotency metadata.
- Existing REST idempotency semantics for same-key/same-request replay and same-key/different-request conflict are preserved.

## Rationale

The old `STARTED` state used the idempotency table as both an in-progress lock and a replay store. That creates the exact stuck-key failure mode when command effects commit but completion does not.

The refined design separates responsibilities:

- PostgreSQL advisory transaction lock controls in-progress concurrency.
- The database transaction controls atomicity of event append and replay result persistence.
- The idempotency record stores only completed results.

This removes the partial durable state while preserving replay behavior and keeping the implementation local to the existing PostgreSQL-backed adapters and application service.
