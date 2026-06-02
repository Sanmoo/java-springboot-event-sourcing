# Credit Account Event Sourcing

A practice project implementing **event sourcing** with **ports and adapters** architecture using:

- **Java 21** + **Spring Boot 4.0.6**
- **PostgreSQL** + **Liquibase**
- **Gradle Kotlin DSL**

## Domain

A revolving credit account with limit reservation:

- Open account, assign/change credit limit
- Authorize purchases (reserve limit)
- Capture or release authorizations
- Receive payments

## Architecture

```
adapter/in/rest/       ← REST controllers, DTOs, exception handler
application/           ← Use cases (command service), ports, results
domain/                ← Aggregate, events, value objects
adapter/out/postgres/  ← Event store, idempotency store
```

### Key Design Decisions

- **Manual event sourcing** with PostgreSQL `event_store` table (JSONB payload)
- **Optimistic locking** via `UNIQUE(aggregate_id, aggregate_version)`
- **Idempotent commands** via `Idempotency-Key` header and idempotency store
- **No read models** in MVP — GET rehydrates aggregate from event store
- **Outbox pattern** planned for future projections/messaging (not yet implemented)

## REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/credit-accounts` | Open a credit account |
| `POST` | `/credit-accounts/{id}/credit-limit` | Assign or change credit limit |
| `POST` | `/credit-accounts/{id}/purchases/authorizations` | Authorize a purchase |
| `POST` | `/credit-accounts/{id}/purchases/authorizations/{authId}/capture` | Capture an authorization |
| `POST` | `/credit-accounts/{id}/purchases/authorizations/{authId}/release` | Release an authorization |
| `POST` | `/credit-accounts/{id}/payments` | Receive a payment |
| `GET` | `/credit-accounts/{id}` | Get account state |

All `POST` endpoints require `Idempotency-Key` header.

## Running

```bash
# Tests (uses Testcontainers — requires Docker)
./gradlew test
```

## Tests

- **Domain tests**: Given-When-Then style, event-sourced aggregate behavior
- **Application tests**: Command service orchestration with fake ports
- **Integration tests**: Real PostgreSQL via Testcontainers (event store + idempotency)
- **REST tests**: Full happy path with `@SpringBootTest(webEnvironment = RANDOM_PORT)`
