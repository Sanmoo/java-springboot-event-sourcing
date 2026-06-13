# Production-style outbox projection pipeline design

## Context

`CreditAccountSummaryProjector` currently detects projection gaps by returning a non-applied tick when an event version is ahead of the currently projected version. `ProjectionWorker` then returns without marking the event as processed or failed. This intentionally avoids applying events out of order, but a permanent gap can leave the same pending event visible forever without clear operational state.

The new design should be closer to a production architecture while keeping the implementation understandable for learning purposes.

## Goals

- Keep `outbox_events` as an immutable transactional event log.
- Support multiple independent consumers/projections for the same event.
- Track processing state per consumer/event.
- Detect projection gaps explicitly and represent them as `BLOCKED`.
- Automatically unblock the next aggregate version after the missing version is processed.
- Support multiple concurrent workers safely.
- Support retry with bounded backoff for transient failures.
- Keep acceptance `.feature` files business-readable; technical behavior belongs in integration tests.

## Non-goals

- Replacing the outbox with a message broker.
- Adding a full operational admin UI.
- Introducing an external job framework such as JobRunr or db-scheduler now.
- Expressing internal delivery states in business-facing Gherkin scenarios.

## Recommended approach

Use four related concepts:

```text
outbox_events
  immutable transactional event log

outbox_consumers
  catalog of enabled consumers

outbox_deliveries
  per-consumer processing state for each event

projection_checkpoints
  per-projection progress by aggregate
```

This keeps event storage separate from delivery state and makes it possible for multiple consumers to process the same event independently.

## Data model

### `outbox_events`

`outbox_events` remains the durable event log. It stores what happened, not whether each downstream consumer processed it.

Core fields:

```text
event_id
aggregate_type
aggregate_id
aggregate_version
event_type
payload
metadata
occurred_at
```

Existing fields such as `processed_at`, `processing_attempts`, and `last_error` become legacy fields for the current single-consumer implementation. The new worker should not depend on them once `outbox_deliveries` exists.

### `outbox_consumers`

New table declaring consumers that should receive deliveries for new events.

Suggested fields:

```text
consumer_name      primary key
description
enabled
created_at
updated_at
```

Initial row:

```text
consumer_name = credit-account-summary-projector
description   = Credit account summary read model projector
enabled       = true
```

### `outbox_deliveries`

New table representing processing state for one event by one consumer.

Logical key:

```text
event_id + consumer_name
```

Suggested fields:

```text
event_id
consumer_name
status              -- PENDING, PROCESSING, PROCESSED, BLOCKED, FAILED
processing_attempts
max_attempts
next_attempt_at
locked_at
locked_by
last_error
blocked_reason
blocked_at
processed_at
failed_at
created_at
updated_at
```

Responsibilities:

- claim work for a worker;
- track retry and backoff;
- record permanent failure;
- record projection gaps as `BLOCKED`;
- allow different consumers to have different states for the same event.

Example:

```text
event_id = E3
consumer_name = credit-account-summary-projector
status = BLOCKED
blocked_reason = Projection gap: expected version 2 but got 3
```

Another consumer may independently have:

```text
event_id = E3
consumer_name = audit-log-publisher
status = PROCESSED
```

### `projection_checkpoints`

New table tracking projection progress per aggregate.

Logical key:

```text
projection_name + aggregate_type + aggregate_id
```

Suggested fields:

```text
projection_name
aggregate_type
aggregate_id
last_projected_version
last_event_id
updated_at
```

For the current projection:

```text
projection_name = credit-account-summary-projector
```

The checkpoint should be updated in the same transaction as the read model and delivery state.

## Delivery creation

Deliveries should be created in the same transaction that appends events and inserts `outbox_events`.

Current event append flow becomes:

```text
transaction
  append event_store row(s)
  insert outbox_events row(s)
  insert outbox_deliveries rows for enabled consumers
commit
```

Conceptual SQL:

```sql
INSERT INTO outbox_deliveries (
    event_id,
    consumer_name,
    status,
    processing_attempts,
    max_attempts,
    next_attempt_at,
    created_at,
    updated_at
)
SELECT
    :event_id,
    c.consumer_name,
    'PENDING',
    0,
    :default_max_attempts,
    now(),
    now(),
    now()
FROM outbox_consumers c
WHERE c.enabled = true;
```

This guarantees that if an event is committed, the deliveries for enabled consumers are committed too.

### Existing events during migration

The migration/backfill should preserve the old operational state for the initial consumer:

```text
outbox_events.processed_at IS NOT NULL -> delivery status PROCESSED
outbox_events.processed_at IS NULL     -> delivery status PENDING
```

This avoids unexpected replay of events already marked processed in the previous model.

## Worker design

### Claiming work

Workers claim deliveries, not raw outbox events.

A claim is an atomic database operation that:

1. finds eligible `PENDING` deliveries;
2. locks them so other workers skip them;
3. marks them `PROCESSING` with `locked_by` and `locked_at`;
4. returns the claimed deliveries to the application.

Use PostgreSQL `FOR UPDATE SKIP LOCKED` inside an `UPDATE ... RETURNING`-style claim.

Conceptual SQL:

```sql
WITH claimable AS (
    SELECT d.event_id, d.consumer_name
    FROM outbox_deliveries d
    JOIN outbox_events e ON e.event_id = d.event_id
    WHERE d.consumer_name = :consumer_name
      AND d.status = 'PENDING'
      AND d.next_attempt_at <= now()
    ORDER BY e.aggregate_type, e.aggregate_id, e.aggregate_version
    FOR UPDATE SKIP LOCKED
    LIMIT :batch_size
)
UPDATE outbox_deliveries d
SET status = 'PROCESSING',
    locked_at = now(),
    locked_by = :worker_id,
    updated_at = now()
FROM claimable c
WHERE d.event_id = c.event_id
  AND d.consumer_name = c.consumer_name
RETURNING d.*;
```

This allows multiple workers to run concurrently without processing the same delivery.

### Transaction boundaries

Use a production-style hybrid:

```text
transaction 1:
  claim a batch of deliveries
commit

for each claimed delivery:
  transaction 2:
    load event, summary, and checkpoint
    process projection
    update summary if needed
    update checkpoint if needed
    transition delivery state
    unblock next delivery if applicable
  commit
```

The claim transaction stays short. Each delivery is then processed atomically in its own transaction.

Claimed deliveries should be processed in aggregate/version order. The SQL claim orders candidates, but the application should not rely on implicit `RETURNING` order; sort the claimed items by `aggregate_type`, `aggregate_id`, and `aggregate_version` before processing them.

If a worker dies after claim but before completion, the delivery remains `PROCESSING` and can be recovered by stale-lock recovery.

## Delivery state machine

States:

```text
PENDING
PROCESSING
PROCESSED
BLOCKED
FAILED
```

Main transitions:

```text
PENDING    -> PROCESSING   claim by worker
PROCESSING -> PROCESSED    success or idempotent duplicate
PROCESSING -> BLOCKED      projection gap
PROCESSING -> PENDING      retryable failure with attempts remaining
PROCESSING -> FAILED       retryable failure after max attempts, or permanent failure
BLOCKED    -> PENDING      missing prior version was processed
PROCESSING -> PENDING      stale processing lock recovered
```

## Gap handling

When processing a projection delivery, the worker loads the projection checkpoint for the event aggregate.

Expected version:

```text
if checkpoint exists:
  expected = checkpoint.last_projected_version + 1
else:
  expected = 1
```

Cases:

```text
event.aggregate_version == expected
  apply projection
  update read model
  update checkpoint
  mark delivery PROCESSED

checkpoint exists and event.aggregate_version <= checkpoint.last_projected_version
  mark delivery PROCESSED without reapplying

event.aggregate_version > expected
  mark delivery BLOCKED
```

A version lower than 1 is invalid event data and should be treated as a permanent failure, not as a projection gap.

A gap does not increment `processing_attempts`, because it is an ordering condition, not a technical failure.

`BLOCKED` fields:

```text
status = BLOCKED
blocked_reason = Projection gap: expected version <expected> but got <actual>
blocked_at = now()
locked_by = null
locked_at = null
last_error = null
updated_at = now()
```

## Automatic unblocking

After processing aggregate version `N`, the worker should attempt to unblock version `N + 1` for the same consumer and aggregate.

Conceptual SQL:

```sql
UPDATE outbox_deliveries d
SET status = 'PENDING',
    blocked_reason = null,
    blocked_at = null,
    next_attempt_at = now(),
    updated_at = now()
FROM outbox_events e
WHERE d.event_id = e.event_id
  AND d.consumer_name = :consumer_name
  AND d.status = 'BLOCKED'
  AND e.aggregate_type = :aggregate_type
  AND e.aggregate_id = :aggregate_id
  AND e.aggregate_version = :processed_version + 1;
```

Only the immediate next version is unblocked. This preserves aggregate ordering.

## Bounded same-aggregate drain

A long `BLOCKED` chain can appear after an event stays `FAILED` for a while. Once the failed event is manually fixed and returned to `PENDING`, processing it unblocks only the next aggregate version. If the worker only waits for the next scheduled cycle, recovery advances one event per cycle for that aggregate.

To avoid slow recovery, the worker should support a bounded same-aggregate drain after a successful projection.

After processing aggregate version `N` and unblocking version `N + 1`, the worker may immediately try to claim the next pending delivery for the same consumer and aggregate:

```text
claimNextPendingForAggregate(
  consumer_name,
  aggregate_type,
  aggregate_id,
  expected_version = N + 1,
  worker_id
)
```

If found, the worker processes that delivery in its own transaction, then repeats the same step for the following version.

This preserves aggregate ordering because it only claims the exact next version. It also improves operational recovery after manual retry of a failed event.

The drain must be bounded so one aggregate cannot monopolize all worker time:

```text
max_consecutive_events_per_aggregate, e.g. 100
max_drain_duration, e.g. 5 seconds
```

Stop draining when:

- there is no next pending delivery for the aggregate;
- the next version is still missing or blocked;
- processing returns `BLOCKED`, `FAILED`, or retryable `PENDING`;
- the consecutive event limit is reached;
- the drain duration limit is reached.

The normal batch claim still exists. Same-aggregate drain is an optimization after successful processing, not a replacement for regular polling.

## Retry and failure handling

Retryable processing failures transition from `PROCESSING` back to `PENDING` while attempts remain.

Fields:

```text
processing_attempts += 1
last_error = truncated error message
next_attempt_at = now() + backoff
locked_by = null
locked_at = null
updated_at = now()
```

Recommended backoff:

```text
min(max_backoff, initial_backoff * 2^(attempt - 1))
```

Suggested defaults:

```text
initial_backoff = 10 seconds
max_backoff = 15 minutes
max_attempts = configurable, e.g. 10
```

When attempts are exhausted:

```text
status = FAILED
failed_at = now()
last_error = truncated error message
locked_by = null
locked_at = null
updated_at = now()
```

`FAILED` does not automatically return to `PENDING`. Manual retry can be added later through an admin operation or SQL maintenance script.

## Stale processing recovery

A scheduled recovery step should return old `PROCESSING` deliveries to `PENDING`.

Criteria:

```text
status = PROCESSING
locked_at < now() - processing_timeout
```

Transition:

```text
status = PENDING
locked_by = null
locked_at = null
next_attempt_at = now()
last_error = Recovered stale PROCESSING lock from worker <worker-id>
updated_at = now()
```

This recovery should not increment `processing_attempts`, because stale locks can be caused by deployment, crash, or shutdown rather than projection failure.

## Observability

Add structured logs around:

- deliveries claimed;
- delivery processed;
- delivery blocked;
- delivery scheduled for retry;
- delivery failed;
- stale delivery recovered;
- blocked delivery unblocked.

`processOnce` should return a richer result instead of an ambiguous count:

```java
record ProjectionWorkerResult(
    int claimed,
    int processed,
    int blocked,
    int retried,
    int failed
) {}
```

If a legacy `int processOnce(int batchSize)` is kept temporarily, it should count only deliveries that reached `PROCESSED`.

## Testing strategy

### Unit tests

Cover projection/checkpoint decisions:

- expected version applies;
- already projected event is idempotent;
- future version becomes gap;
- no checkpoint expects version 1;
- version greater than 1 without checkpoint becomes gap.

### Integration tests

Cover technical behavior in repository/worker tests:

- claim marks deliveries `PROCESSING`;
- two workers do not claim the same delivery;
- successful processing updates summary, checkpoint, and delivery;
- idempotent duplicate marks delivery `PROCESSED` without reapplying;
- gap marks delivery `BLOCKED`;
- processing the missing version unblocks the next version;
- after manual retry of a failed event, same-aggregate drain can process a bounded blocked chain without waiting one scheduler cycle per event;
- retryable failure returns to `PENDING` with backoff;
- exhausted attempts become `FAILED`;
- stale `PROCESSING` deliveries recover to `PENDING`.

### Migration verification

Do not add detailed migration tests to every fast build.

Recommended levels:

- normal integration tests continue to validate that Liquibase can create the schema from scratch;
- optional tagged migration/backfill test can validate essential old-state preservation:
  - old `processed_at IS NOT NULL` becomes delivery `PROCESSED`;
  - old `processed_at IS NULL` becomes delivery `PENDING`.

The optional test can run in CI/release workflows rather than every local build if it becomes expensive.

### E2E / acceptance tests

Keep `.feature` files business-readable and high-level.

Acceptance scenarios should validate externally observable behavior, such as:

- account activity eventually appears in the account summary;
- the API returns the expected business response when a summary is not ready.

Do not mention internal concepts such as:

- `outbox_deliveries`;
- `PROCESSING`;
- `BLOCKED`;
- locks;
- checkpoints;
- worker implementation details.

Technical gap/block/unblock behavior belongs in integration tests, not business-facing Gherkin scenarios.

## Open implementation notes

- The SQL details should live in the Postgres adapter layer.
- Core projection code should depend on ports and domain records, not SQL concepts.
- Consumer name should be a constant/configuration value for the summary projector.
- Adding a future consumer requires:
  1. inserting a row in `outbox_consumers`;
  2. deciding whether it needs historical events;
  3. running a backfill into `outbox_deliveries` if it needs history;
  4. starting its worker.

## Approval status

Approved design direction from discussion:

- use explicit per-consumer delivery state;
- use both `outbox_deliveries` and `projection_checkpoints` from the start;
- support concurrent workers with database row claiming;
- use retry with bounded backoff;
- avoid technical Gherkin scenarios for internal projection mechanics;
- add bounded same-aggregate drain so long blocked chains recover efficiently after the failed event is fixed.
