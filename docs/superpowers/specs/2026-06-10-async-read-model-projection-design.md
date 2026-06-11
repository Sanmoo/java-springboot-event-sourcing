# Async Read Model Projection Design

## Context

The current MVP exposes `GET /credit-accounts/{id}` by rehydrating the `CreditAccount` aggregate from the `event_store`. That is acceptable for learning event sourcing, but CQRS is not fully visible while reads still depend on the command aggregate.

The next increment introduces an asynchronous read model:

```text
event_store + outbox_events -> projector -> credit_account_summary
```

The design intentionally skips synchronous read model updates. The command side persists facts durably, and the query side catches up asynchronously.

## Goals

- Make `GET` endpoints read from a projection instead of rehydrating aggregates.
- Introduce a durable outbox as the bridge from stored domain events to projections.
- Keep command-side business invariants strongly consistent through aggregate rehydration from the event store.
- Make eventual consistency explicit through `projectedVersion` and optional `minVersion` reads.
- Add an initial paginated `GET /credit-accounts` list endpoint without filters.
- Rename existing port interfaces to more expressive names.

## Non-goals

- Kafka, RabbitMQ, or another external broker.
- Synchronous projection updates in the command transaction.
- Multiple projections.
- List filters.
- Automatic projection rebuild tooling.
- Dead-letter table.
- Outbox status/admin endpoint.
- Using read models to validate commands.

## Architecture

Commands continue to use the event-sourced aggregate:

```text
POST command
  -> load aggregate history from event_store
  -> rehydrate aggregate
  -> validate business invariants
  -> append events to event_store
  -> append matching rows to outbox_events in the same DB transaction
  -> return post-command state from the aggregate
```

Projection is asynchronous:

```text
scheduled projector
  -> poll pending outbox_events
  -> apply each event to credit_account_summary
  -> mark outbox row as processed
```

Queries read only the read model:

```text
GET /credit-accounts/{id}
GET /credit-accounts
  -> credit_account_summary
```

The `GET` path must not fall back to aggregate rehydration. This keeps the command/query separation visible and testable.

## Data model

### `outbox_events`

Durable queue of domain events to be consumed by projectors and, later, external publishers.

Columns:

```text
outbox_events
- event_id UUID primary key
- aggregate_type varchar not null
- aggregate_id varchar not null
- aggregate_version bigint not null
- event_type varchar not null
- payload jsonb not null
- metadata jsonb not null default '{}'
- occurred_at timestamptz not null
- processed_at timestamptz null
- processing_attempts int not null default 0
- last_error text null
```

Constraints and indexes:

```text
unique (aggregate_type, aggregate_id, aggregate_version)
index pending rows where processed_at is null, ordered by occurred_at/event_id
```

`event_id` should match the corresponding `event_store.event_id`. This gives a stable idempotency key across the event store, outbox, and projector.

### `credit_account_summary`

Read model for account details and listing.

Columns:

```text
credit_account_summary
- credit_account_id uuid primary key
- opened boolean not null
- credit_limit numeric(19,2) null
- outstanding_balance numeric(19,2) not null
- authorized_amount numeric(19,2) not null
- available_limit numeric(19,2) not null
- authorizations jsonb not null
- projected_version bigint not null
- last_event_id uuid not null
- updated_at timestamptz not null
```

`authorizations` stays as JSONB for this increment because the current API returns the account summary with an embedded authorization list. If future endpoints query authorizations independently, a separate authorization summary table can be introduced.

## Projection behavior

The projector processes outbox rows in small batches. It should use one transaction per event.

For each event:

```text
begin transaction
  load current credit_account_summary row
  apply event if it is the next aggregate version
  update/insert credit_account_summary
  mark outbox_events.processed_at
commit
```

### Ordering and idempotency

Rules:

```text
summary missing:
  event aggregate_version must be 1 to create the row

summary.projected_version + 1 == event.aggregate_version:
  apply event normally

summary.projected_version >= event.aggregate_version:
  event is already reflected; mark outbox row processed without changing summary

summary.projected_version + 1 < event.aggregate_version:
  event is out of order; leave pending or record a retryable failure
```

A projection failure never invalidates or reverts the aggregate. The event store remains the source of truth. Projection failures mean the read model is stale or the projector has a bug. After fixing projector code, pending events can be retried; future rebuild tooling can reconstruct `credit_account_summary` from durable events.

### Event handlers

The summary projector must handle all current events:

- `CreditAccountOpened`: create summary with zero balances and version 1.
- `CreditLimitAssigned`: set credit limit and available limit.
- `CreditLimitChanged`: update credit limit and available limit.
- `PurchaseAuthorized`: add authorization, increase authorized amount, reduce available limit.
- `PurchaseCaptured`: mark authorization captured, reduce authorized amount, increase outstanding balance.
- `PurchaseAuthorizationReleased`: mark authorization released, reduce authorized amount, increase available limit.
- `PaymentReceived`: reduce outstanding balance and increase available limit.

The projected state should match the current response shape, plus projection metadata.

## HTTP contract

### Command responses

POST endpoints remain synchronous on the command side. They return the post-command state computed from the aggregate and include the confirmed aggregate version.

Example:

```json
{
  "creditAccountId": "...",
  "opened": true,
  "creditLimit": "1000.00",
  "outstandingBalance": "0.00",
  "authorizedAmount": "0.00",
  "availableLimit": "1000.00",
  "aggregateVersion": 2,
  "authorizations": []
}
```

Clients can use this version for read-your-writes checks:

```http
GET /credit-accounts/{id}?minVersion=2
```

### `GET /credit-accounts/{id}` without `minVersion`

Reads only `credit_account_summary`.

```text
summary exists -> 200 OK
summary missing -> 404 Not Found
```

Response includes `projectedVersion`:

```json
{
  "creditAccountId": "...",
  "opened": true,
  "creditLimit": "1000.00",
  "outstandingBalance": "100.00",
  "authorizedAmount": "50.00",
  "availableLimit": "850.00",
  "projectedVersion": 5,
  "authorizations": []
}
```

### `GET /credit-accounts/{id}?minVersion=N`

Reads only `credit_account_summary`, but treats missing or stale summaries as projection lag.

```text
summary exists and projectedVersion >= N -> 200 OK
summary exists and projectedVersion < N -> 202 Accepted
summary missing -> 202 Accepted
```

Example `202`:

```json
{
  "message": "Projection not ready",
  "creditAccountId": "...",
  "currentProjectionVersion": 3,
  "requiredVersion": 5
}
```

If the summary is missing:

```json
{
  "message": "Projection not ready",
  "creditAccountId": "...",
  "currentProjectionVersion": null,
  "requiredVersion": 1
}
```

This avoids returning `202` forever for arbitrary unknown IDs unless the caller explicitly asks for a minimum version.

### `GET /credit-accounts`

Add a paginated list endpoint without filters.

Request:

```http
GET /credit-accounts?page=0&size=20
```

Defaults and limits:

```text
page default: 0
size default: 20
max size: 100
size > 100: 400 Bad Request
```

Ordering:

```text
ORDER BY updated_at DESC, credit_account_id ASC
```

Response:

```json
{
  "items": [
    {
      "creditAccountId": "...",
      "opened": true,
      "creditLimit": "1000.00",
      "outstandingBalance": "100.00",
      "authorizedAmount": "50.00",
      "availableLimit": "850.00",
      "projectedVersion": 5,
      "authorizations": []
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

The list is eventually consistent. Recently created or updated accounts may not appear until their outbox events are projected.

## Ports and naming

Keep interfaces in `core/port/`, but remove generic `Port` suffixes from type names.

Rename existing interfaces:

```text
EventStorePort -> EventStore
IdempotencyPort -> IdempotencyRepository
```

Final interfaces:

```text
core/port/EventStore.java
core/port/IdempotencyRepository.java
core/port/OutboxEventRepository.java
core/port/CreditAccountSummaryRepository.java
core/port/UniqueIdGenerator.java
```

Adapters:

```text
JdbcEventStoreAdapter implements EventStore
JdbcIdempotencyAdapter implements IdempotencyRepository
JdbcOutboxEventAdapter implements OutboxEventRepository
JdbcCreditAccountSummaryAdapter implements CreditAccountSummaryRepository
```

### `EventStore`

Source of truth for domain events:

```java
List<EventEnvelope> loadEvents(String aggregateType, String aggregateId);

AppendResult appendEvents(
    String aggregateType,
    String aggregateId,
    long expectedVersion,
    List<CreditAccountEvent> events,
    Map<String, String> metadata
);
```

`JdbcEventStoreAdapter.appendEvents(...)` inserts into `event_store` and `outbox_events` in the same transaction.

### `IdempotencyRepository`

Current idempotency behavior:

```java
void lockKey(String idempotencyKey);
Optional<IdempotencyRecord> findByKey(String idempotencyKey);
void saveResult(...);
```

### `OutboxEventRepository`

Used only by the projection worker for consumption and processing state:

```java
List<OutboxEvent> findPending(int limit);
void markProcessed(UUID eventId);
void markFailed(UUID eventId, String error);
```

It intentionally has no public `save` method. Writing outbox rows is a transactional detail of `JdbcEventStoreAdapter`.

### `CreditAccountSummaryRepository`

Read model access:

```java
Optional<CreditAccountSummary> findById(CreditAccountId id);
void upsert(CreditAccountSummary summary);
CreditAccountSummaryPage findAll(CreditAccountSummaryPageRequest request);
```

Use core-owned paging DTOs instead of Spring Data types in the port:

```text
CreditAccountSummaryPageRequest(page, size)
CreditAccountSummaryPage(items, page, size, totalItems, totalPages)
```

## Components

New or changed components:

```text
core/projection/CreditAccountSummaryProjector
adapter/in/scheduler/OutboxProjectionWorker
adapter/out/postgres/JdbcOutboxEventAdapter
adapter/out/postgres/JdbcCreditAccountSummaryAdapter
```

`GetCreditAccountUseCase` should keep its external role but change implementation to read from `CreditAccountSummaryRepository` instead of rehydrating the aggregate.

Add a list use case, for example:

```text
ListCreditAccountsUseCase
```

The controller maps `GET /credit-accounts` to this use case.

## Configuration

Add projection configuration:

```yaml
credit-account:
  projections:
    enabled: true
    poll-interval: 1s
    batch-size: 50
    max-attempts: 10
```

Tests should be able to disable the scheduler and invoke the worker/projector directly.

## Testing strategy

### Event store and outbox

Verify `appendEvents(...)` writes both `event_store` and `outbox_events` transactionally, preserving:

- `event_id`
- `aggregate_type`
- `aggregate_id`
- `aggregate_version`
- `event_type`
- payload
- metadata
- `occurred_at`

Optimistic locking behavior must continue to work.

### Projector

Cover every event handler and the ordering/idempotency rules:

- version 1 creates a summary;
- next version updates summary;
- repeated event is idempotent;
- out-of-order event does not corrupt summary;
- failed projection increments attempts and stores `last_error`.

### `GET /credit-accounts/{id}`

Test:

```text
without minVersion:
  summary exists -> 200
  summary missing -> 404

with minVersion:
  summary projectedVersion >= minVersion -> 200
  summary projectedVersion < minVersion -> 202
  summary missing -> 202
```

### `GET /credit-accounts`

Test:

- default paging;
- explicit `page` and `size`;
- `size > 100` returns `400`;
- ordering by `updated_at DESC, credit_account_id ASC`.

### End-to-end projection flow

Test a realistic flow:

```text
POST /credit-accounts
GET /credit-accounts/{id}?minVersion=1 may return 202 before projection
run/await projector
GET /credit-accounts/{id}?minVersion=1 returns 200
GET /credit-accounts includes the account
```

## Future work

- Projection rebuild command or admin endpoint.
- Dead-letter handling after repeated projector failures.
- External broker publication from outbox.
- Additional read models and list filters.
- Dedicated authorization summary table if authorization query endpoints are added.
