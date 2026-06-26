# Global Event Position — Design

**Date**: 2026-06-26
**Status**: Approved

## Motivation

The event store currently guarantees ordering inside each aggregate via
`(aggregate_type, aggregate_id, aggregate_version)`. Upcoming projection
rebuild and incremental replay workflows need a **stable global ordering**
across all stored events, independent of aggregate boundaries.

## Goal

Introduce a monotonically increasing `global_position` on every event
persisted in `event_store` that represents append order in the event log.

## Design

### Database

| Decision                 | Choice                                                                 |
|--------------------------|------------------------------------------------------------------------|
| Column type              | `BIGINT GENERATED ALWAYS AS IDENTITY` (PostgreSQL-native identity)     |
| Constraints              | `NOT NULL`, `UNIQUE`                                                   |
| Index                    | B-tree on `global_position` for `WHERE global_position > ? ORDER BY`  |
| Existing rows            | Auto-backfilled by PostgreSQL on `ADD GENERATED ALWAYS AS IDENTITY`    |
| Outbox table             | No change — outbox ordering stays per-aggregate                        |

Liquibase migration `011-add-global-position.yaml`:

1. `addColumn`: `global_position BIGINT NOT NULL`
2. `sql`: `ALTER TABLE event_store ALTER COLUMN global_position ADD GENERATED ALWAYS AS IDENTITY`
3. `addUniqueConstraint`: `uq_event_store_global_position`
4. `createIndex`: `idx_event_store_global_position`

### Model

`EventEnvelope` gains `long globalPosition`:

```java
public record EventEnvelope(
    UUID eventId,
    String aggregateType,
    String aggregateId,
    long aggregateVersion,
    long globalPosition,       // NEW
    CreditAccountEvent event,
    Instant occurredAt,
    Map<String, String> metadata
) {}
```

`AppendResult` — **unchanged**. `globalPosition` is only consumed when
reading events back, not during append.

### Interface

`EventStore` gains one method:

```java
List<EventEnvelope> loadEventsAfter(long globalPosition, int limit);
```

Returns events where `global_position > globalPosition`, ordered by
`global_position ASC`, limited to `limit` rows.

### Adapter

`JdbcEventStoreAdapter`:

- `LOAD_EVENTS_SQL` — extended to include `global_position`
- `INSERT_EVENT_SQL` — unchanged (identity column auto-filled)
- New `LOAD_EVENTS_AFTER_SQL` — `WHERE global_position > ? ORDER BY global_position ASC LIMIT ?`
- `mapEventEnvelope` — reads `rs.getLong("global_position")`
- `loadEventsAfter` — delegates to `jdbcTemplate.query(LOAD_EVENTS_AFTER_SQL, ...)`

### Call sites

Only `JdbcEventStoreAdapter.mapEventEnvelope()` constructs `EventEnvelope`.
Test mocks return `EventEnvelope` via `when().thenReturn()` and do not
construct them directly — no changes needed there.

## Tests

Three new tests in `JdbcEventStoreAdapterIT`:

1. **global_position is populated** — append 3 events across 2 aggregates,
   query raw `global_position` column, assert 3 distinct non-null
   monotonic values.

2. **global_position exposed on load** — append 1 event, call
   `loadEvents()`, assert `envelope.globalPosition() > 0`.

3. **loadEventsAfter returns correct slice** — append 5 events, capture
   positions, call `loadEventsAfter(pos2, 2)`, assert exactly events 3
   and 4 returned in correct order.

## Acceptance criteria

- Every new event persisted in `event_store` receives a unique
  `global_position`.
- Events can be queried in stable global order via `loadEventsAfter`.
- Existing aggregate rehydration by `aggregate_version` continues
  working unchanged.
- `./gradlew check` passes (all tests, checkstyle, PMD, SpotBugs,
  ErrorProne, PiTest).
