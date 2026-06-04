# UUID v7 Identifier Generation Design

## Context

Production code currently generates UUIDs with `UUID.randomUUID()` in three places:

- `CreditAccountId.newId()`
- `JdbcEventStoreAdapter.appendEvents()` for `event_id`

UUID v4 values are random. They work as identifiers, but they are not time-ordered and can produce less index-friendly write patterns in relational databases than ordered identifiers. The goal is to use UUID v7 for production-generated identifiers while keeping the change small and aligned with the existing ports-and-adapters architecture.

Test fixtures may continue to use `UUID.randomUUID()` when they only need distinct values. This design targets production code only. UUIDs supplied by API clients are out of scope: the system must accept and persist them as provided rather than replacing them with UUID v7 values.

## Goals

- Replace production UUID generation with UUID v7.
- Introduce a minimal abstraction for unique identifier generation.
- Keep domain value objects simple and independent from UUID generation libraries.
- Preserve existing persisted UUID column types and API representations.
- Add tests before implementation to prove the new generation path is used.

## Non-goals

- Replace every `UUID.randomUUID()` usage in tests.
- Migrate existing database rows.
- Change REST API formats or database column types.
- Add a broader ID framework beyond UUID generation.

## Recommended Approach

Create a minimal core port:

```java
package com.sanmoo.eventsourcing.creditaccount.core.port;

import java.util.UUID;

public interface UniqueIdGenerator {
    UUID generate();
}
```

Create one production adapter that implements this port with UUID v7. The adapter should live outside the domain, for example under `adapter/out/uuid`, and be registered as a Spring bean.

Production code that needs a newly generated identifier should receive `UniqueIdGenerator` through constructor injection and wrap the generated UUID in the appropriate value object.

## Component Changes

### Core Port

Add `UniqueIdGenerator` in `core.port`. The port returns `UUID` rather than a domain-specific type because it is used by multiple production-generated identifier types, such as aggregate IDs and event IDs.

### UUID v7 Adapter

Add a Spring component such as `UuidV7Generator` that implements `UniqueIdGenerator`.

The implementation should use a maintained UUID v7 library rather than hand-rolling bit manipulation. The selected dependency must be small and compatible with Java 25/Spring Boot 4. The adapter is the only production class that should depend on that library.

### Domain Model

Keep value objects responsible for validation/wrapping only:

- `CreditAccountId.of(UUID)` remains.
- `AuthorizationId.of(UUID)` remains.
- Production use cases should not call static random generation methods for system-generated identifiers.

If `newId()` becomes unused in production, remove it or refactor it away so production code cannot accidentally generate UUID v4 through the domain model.

### Use Cases

Inject `UniqueIdGenerator` into use cases that create identifiers:

- `OpenCreditAccountUseCase` generates `CreditAccountId` with `CreditAccountId.of(uniqueIdGenerator.generate())`.

`AuthorizePurchaseUseCase` does not create an identifier in the current API contract. `AuthorizationId` is supplied by the API client in the authorize purchase request, remains part of `AuthorizePurchaseInput`, and is passed through to the domain unchanged. Other use cases that only receive IDs from inputs should not depend on the generator.

### Event Store Adapter

Inject `UniqueIdGenerator` into `JdbcEventStoreAdapter` and use it to create `event_id` values during append.

This keeps event IDs ordered by generation time while preserving the existing `uuid` database column type.

## Data Flow

1. A command enters a REST controller.
2. The controller calls the appropriate use case.
3. If the use case creates a new domain identifier, it calls `UniqueIdGenerator.generate()`.
4. The use case wraps generated UUIDs in the appropriate value object, such as `CreditAccountId`. API-supplied UUIDs, such as authorize purchase `AuthorizationId`, are parsed at the boundary and passed through unchanged.
5. When events are appended, `JdbcEventStoreAdapter` calls the same port to generate each `event_id`.
6. The UUID values are persisted and returned using the same formats as today.

## Error Handling

The generator should not normally fail. If the underlying UUID library throws a runtime exception, allow it to propagate as an infrastructure failure. No domain-specific error is needed.

Constructor injection should make missing Spring wiring fail at application startup or test context creation.

## Testing Plan

Follow TDD before production edits:

1. Add a test for the UUID v7 adapter proving generated UUIDs have version `7`.
2. Add or update use case tests with a deterministic `UniqueIdGenerator` fake to prove `OpenCreditAccountUseCase` uses the generated account ID.
3. Restore/keep authorize purchase tests proving caller-provided `AuthorizationId` is accepted and returned unchanged.
4. Add or update event store adapter testing to prove `JdbcEventStoreAdapter` inserts the `event_id` provided by the generator.
5. Run focused tests first, then the broader Gradle verification appropriate for the touched code.

Existing tests may keep using `UUID.randomUUID()` as fixture data unless they exercise production ID generation behavior.

## Acceptance Criteria

- No production code calls `UUID.randomUUID()`.
- Production-generated account IDs and event IDs come from `UniqueIdGenerator`.
- API-supplied authorization IDs are accepted and preserved; they are not generated or converted to UUID v7 by the system.
- The production generator creates UUID v7 values.
- Domain model classes do not depend on a UUID v7 library.
- Tests cover the new generator and the use of the port in production generation paths.
