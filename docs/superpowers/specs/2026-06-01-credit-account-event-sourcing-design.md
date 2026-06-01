# Credit Account Event Sourcing Design

Date: 2026-06-01

## Context

This repository is a practice project for event sourcing using:

- Java latest LTS
- Spring Boot latest stable version
- PostgreSQL latest version
- Liquibase latest version
- Gradle Kotlin DSL

The architecture will follow ports and adapters. The domain layer will be implemented with event sourcing.

The chosen domain is a credit account with revolving limit behavior.

## Scope

The MVP covers:

- Opening a credit account.
- Assigning an initial credit limit.
- Changing the credit limit.
- Authorizing purchases by reserving available limit.
- Capturing authorized purchases.
- Releasing purchase authorizations.
- Receiving payments.
- Idempotency for external commands.
- Manual event sourcing with PostgreSQL and Liquibase.
- REST as the first inbound adapter.
- Future readiness for messaging, read models, projections, and outbox.

The MVP does not include:

- Persistent CQRS read models.
- Asynchronous messaging adapters.
- Outbox relay implementation.
- Billing cycles, invoices, interest, fees, delinquency, or fraud analysis.

## Architectural Approach

The project will be a Spring Boot monolith organized with ports and adapters.

### Domain

The domain is isolated from Spring, PostgreSQL, HTTP, JSON serialization, and framework annotations.

It contains:

- The event-sourced aggregate `CreditAccount`.
- Domain commands or aggregate methods.
- Event sourcing events for the aggregate.
- Business invariants.
- Rehydration logic from historical events.

### Application

The application layer exposes use cases oriented around commands.

It coordinates:

- Idempotency checks.
- Loading aggregate history through a port.
- Rehydrating the aggregate.
- Executing domain behavior.
- Appending new events with optimistic locking.
- Returning command results to inbound adapters.

### Inbound Adapters

The initial inbound adapter is REST.

A future messaging adapter may call the same application use cases without changing the domain.

### Outbound Adapters

Initial outbound adapters are:

- PostgreSQL event store.
- PostgreSQL idempotency store.
- Liquibase migrations.

Future outbound mechanisms may include:

- Outbox table.
- Outbox relay.
- Projection/read model storage.
- Broker publishing adapter.

## Domain Model

The main aggregate is `CreditAccount`, identified by `creditAccountId`.

Its state is derived from persisted events:

- `status`: nonexistent/opened.
- `creditLimit`: current granted limit.
- `outstandingBalance`: captured debt balance.
- `authorizedAmount`: total amount reserved by open purchase authorizations.
- `authorizations`: authorization records by `authorizationId` with amount and status.

The main calculation is:

```text
availableLimit = creditLimit - outstandingBalance - authorizedAmount
```

## Events

Initial aggregate event sourcing events:

- `CreditAccountOpened`
- `CreditLimitAssigned`
- `CreditLimitChanged`
- `PurchaseAuthorized`
- `PurchaseCaptured`
- `PurchaseAuthorizationReleased`
- `PaymentReceived`

These events are facts of the `CreditAccount` domain and are persisted in the event store to rebuild aggregate state.

## Commands and Rules

### OpenCreditAccount

Rules:

- The account must not already exist.

Emits:

- `CreditAccountOpened`

### AssignCreditLimit

Rules:

- The account must be opened.
- The limit must be positive.
- The initial limit must not already be assigned.

Emits:

- `CreditLimitAssigned`

### ChangeCreditLimit

Rules:

- The account must be opened.
- The new limit must be positive.
- The new limit must not be lower than `outstandingBalance + authorizedAmount`.
- The MVP does not allow an intentional over-limit state.

Emits:

- `CreditLimitChanged`

### AuthorizePurchase

Rules:

- The account must be opened.
- A credit limit must be assigned.
- The amount must be positive.
- The amount must fit within `availableLimit`.
- The `authorizationId` must not already exist.

Emits:

- `PurchaseAuthorized`

### CapturePurchase

Rules:

- The authorization must exist.
- The authorization must be open.
- The MVP captures the full authorized amount only.

Effects:

- Decreases reserved amount.
- Increases outstanding balance.

Emits:

- `PurchaseCaptured`

### ReleasePurchaseAuthorization

Rules:

- The authorization must exist.
- The authorization must be open.

Effects:

- Releases the reserved amount.

Emits:

- `PurchaseAuthorizationReleased`

### ReceivePayment

Rules:

- The account must be opened.
- The amount must be positive.
- Payment cannot exceed the outstanding balance in the MVP.
- The MVP does not allow positive account credit.

Effects:

- Reduces outstanding balance.

Emits:

- `PaymentReceived`

## Event Store

The event store is append-only and stored in PostgreSQL.

The project will use aggregate terminology in persistence names.

Suggested `event_store` columns:

- `event_id` UUID
- `aggregate_id` UUID or string
- `aggregate_type` text
- `aggregate_version` bigint
- `event_type` text
- `payload` jsonb
- `metadata` jsonb
- `occurred_at` timestamp with time zone

Optimistic locking is enforced with a unique constraint on:

```text
(aggregate_id, aggregate_version)
```

Application flow:

1. Load events for an aggregate.
2. Rehydrate the aggregate and determine its current version.
3. Execute the command.
4. Append new events with the expected aggregate version.
5. Let the database reject concurrent writes through the unique constraint.

The MVP will not perform automatic retry on version conflicts.

## Serialization

Domain events are explicit Java types.

The PostgreSQL adapter maps:

- Java event class to `event_type`.
- Event instance to JSONB `payload`.
- JSONB payload back to the correct Java event class.

The domain does not depend on JSON, Jackson, JDBC, JPA, or PostgreSQL.

## Idempotency

Every external command requires an idempotency key.

Suggested `idempotency_records` columns:

- `idempotency_key`
- `command_type`
- `aggregate_id`
- `request_hash`
- `status`
- `response_payload`
- `created_at`
- `completed_at`

Behavior:

1. The REST adapter receives an `Idempotency-Key` header.
2. The application checks the idempotency store.
3. If the key exists with the same request hash and completed status, the previous result is returned.
4. If the key exists with a different request hash, the command fails with an idempotency conflict.
5. If the key does not exist, the application records the command start, executes the use case, appends events, and stores the result.

## Application Ports

### EventStorePort

Responsibilities:

- Load aggregate events.
- Append aggregate events with expected version.

Conceptual operations:

```text
loadEvents(aggregateType, aggregateId)
appendEvents(aggregateType, aggregateId, expectedVersion, events, metadata)
```

### IdempotencyPort

Responsibilities:

- Reserve or start processing an idempotency key.
- Find completed results for repeated commands.
- Detect key reuse with a different payload.
- Store successful or failed command results according to application policy.

### No DomainEventPublisherPort in MVP

The design intentionally does not include a `DomainEventPublisherPort`.

Future publication will use the outbox pattern rather than direct publishing after event persistence.

## REST API

Initial endpoints:

```text
POST /credit-accounts
POST /credit-accounts/{id}/credit-limit
POST /credit-accounts/{id}/purchases/authorizations
POST /credit-accounts/{id}/purchases/authorizations/{authorizationId}/capture
POST /credit-accounts/{id}/purchases/authorizations/{authorizationId}/release
POST /credit-accounts/{id}/payments
GET  /credit-accounts/{id}
```

The `GET /credit-accounts/{id}` endpoint rehydrates the aggregate from the event store and returns derived state:

- Current credit limit.
- Outstanding balance.
- Reserved amount.
- Available limit.
- Open authorizations.

## Event Sourcing Events, Domain Events, and Future Outbox

The MVP uses only aggregate event sourcing events.

Conceptual distinction for future work:

- Event sourcing events are persisted in the aggregate history and used to rebuild state.
- Domain events are business facts relevant to domain policies, processes, or other components.

When a future domain event is part of the aggregate's consistent history, it should also be modeled as a persisted event sourcing event.

Example future event sequence:

```text
PurchaseCaptured
CreditUtilizationThresholdReached
PaymentReceived
CreditUtilizationReturnedBelowThreshold
```

In this example, `CreditUtilizationThresholdReached` is both:

- A domain event.
- A persisted event in the aggregate history.
- A safe source for outbox publication.

The aggregate must not publish events as side effects during historical rehydration. Applying events during rehydration only rebuilds state.

Future outbox flow:

1. Append aggregate events to `event_store`.
2. In the same database transaction, create outbox records derived from persisted events.
3. An outbox relay processes unpublished records.
4. The relay delivers messages to projectors, read models, brokers, or integrations.

Outbox records are publication envelopes or messages based on persisted events. They are not the source of truth.

## Error Handling

Domain errors are explicit and independent of HTTP.

The REST adapter translates them to HTTP responses:

- Account already exists: `409 Conflict`.
- Account not found: `404 Not Found`.
- Insufficient limit: `422 Unprocessable Entity`.
- Invalid authorization state: `422 Unprocessable Entity`.
- Payment exceeds outstanding balance: `422 Unprocessable Entity`.
- Idempotency key reused with different request payload: `409 Conflict`.
- Optimistic locking conflict: `409 Conflict`.

## Testing Strategy

### Domain Tests

Use Given-When-Then style:

- Given historical events.
- When a command is executed.
- Then expected new events are emitted or an expected domain error occurs.

### Application Tests

Verify that use cases:

- Load existing events.
- Rehydrate the aggregate.
- Enforce idempotency.
- Append events with expected aggregate version.
- Return command results.

### PostgreSQL Integration Tests

Use Testcontainers to verify:

- Liquibase creates the schema.
- Events can be appended and loaded.
- JSONB serialization works.
- The unique constraint on `(aggregate_id, aggregate_version)` enforces optimistic locking.
- Idempotency records persist and return previous results.

### REST Tests

Cover:

- Main happy path.
- Basic domain error translation.
- Idempotency header behavior.

## Open Decisions Deferred

The following decisions are intentionally deferred:

- Exact Java package names.
- Whether persistence uses JDBC, jOOQ, or Spring Data JDBC.
- Exact event payload versioning strategy.
- Exact response DTO shape.
- Outbox schema and relay implementation.
- Read model schema.
- Messaging technology.
