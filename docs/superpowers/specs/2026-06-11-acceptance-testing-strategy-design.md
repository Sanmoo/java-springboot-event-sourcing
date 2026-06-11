# Acceptance Testing Strategy Design

## Context

The project already has unit tests, integration tests with Testcontainers, architecture fitness functions, and REST integration tests. These tests provide useful technical coverage, but they are not organized as an explicit acceptance/regression suite that communicates business behavior in a form that non-implementation stakeholders can review.

The goal is to introduce an automated acceptance suite that increases confidence when tasks are completed. The suite should validate important observable behavior through the real HTTP API, including the asynchronous read model used by the event-sourced architecture.

## Chosen Approach

Use Cucumber/Gherkin as a business-readable acceptance layer, executed against the real Spring Boot application.

The acceptance tests will run with:

- Cucumber on the JUnit Platform.
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- PostgreSQL through the existing Testcontainers configuration.
- HTTP calls to the application endpoints only.
- Polling through HTTP for eventually consistent projections.

The suite must not call application internals such as use cases, repositories, event stores, or projection workers from step definitions.

## Alternatives Considered

### Cucumber over application use cases

This would be faster and less dependent on HTTP details, but it would not validate the REST API contract, serialization, request headers, status codes, exception handling, Spring wiring, event persistence, outbox processing, or read model query behavior. It does not meet the desired confidence level for acceptance testing.

### Black-box Cucumber against an externally running application

This would be closer to production, but it would require external orchestration and would be harder to run locally and in CI as the first automated acceptance suite. It may be useful later for smoke tests in deployed environments.

## Proposed Test Structure

```text
src/test/resources/features/
  credit-account-lifecycle.feature
  credit-account-business-rules.feature
  credit-account-consistency.feature

src/test/java/com/sanmoo/eventsourcing/creditaccount/acceptance/
  CucumberAcceptanceTest.java
  AcceptanceTestContext.java
  CreditAccountStepDefinitions.java
  AcceptanceHttpClient.java
```

### Feature Files

The feature files describe user-observable behavior in Portuguese using Gherkin. They should avoid implementation details and should express domain outcomes such as available limit, authorized amount, outstanding balance, idempotency behavior, and projection consistency.

### CucumberAcceptanceTest

The runner that connects Cucumber to the JUnit Platform and Spring Boot test context.

### AcceptanceTestContext

Stores per-scenario state such as:

- credit account ID;
- authorization ID;
- latest idempotency key;
- latest HTTP response;
- latest expected projected version.

The context must be scenario-scoped so one scenario cannot depend on another.

### AcceptanceHttpClient

A small test client that encapsulates repetitive HTTP concerns:

- base URL construction from the random server port;
- request headers;
- idempotency keys;
- typed command/query helper methods;
- eventual polling for projected summaries.

### CreditAccountStepDefinitions

Step definitions translate Gherkin steps into HTTP calls and assertions. They should be thin and should delegate repetitive HTTP mechanics to `AcceptanceHttpClient`.

## Asynchronous Projection Strategy

The acceptance suite must treat the projection as part of the real application. It should not call `ProjectionWorker.processOnce()` or any other internal component to advance the read model.

When a command returns a version that the read model must eventually reflect, the test stores that version and later polls:

```http
GET /credit-accounts/{id}?minVersion={expectedVersion}
```

Expected behavior:

- before the projection reaches the requested version, the API may return `202 Accepted`;
- once the projection reaches the requested version, the API returns `200 OK` with the account summary;
- if the expected version is not reached before the timeout, the test fails with a clear diagnostic message.

Default polling settings:

- polling interval: 100ms to 200ms;
- timeout: 5s;
- configurable through test properties if CI proves slower.

The production projection scheduler currently uses `credit-account.projections.poll-interval: 1s`, so typical waits should be around one to two seconds when the scheduler is active.

## Initial Acceptance Scenarios

### Credit Account Lifecycle

File: `credit-account-lifecycle.feature`

Covers the main happy path:

1. open a credit account;
2. assign a credit limit;
3. authorize a purchase;
4. eventually verify authorized amount and available limit in the summary;
5. capture the authorization;
6. eventually verify outstanding balance and available limit;
7. receive a partial payment;
8. eventually verify the updated outstanding balance and available limit.

Example:

```gherkin
Cenário: Cliente usa uma conta de crédito do início ao pagamento parcial
  Dado que uma conta de crédito foi aberta
  E o limite de crédito da conta é "500.00"
  Quando uma compra de "100.00" é autorizada no estabelecimento "Store"
  Então eventualmente o resumo da conta deve mostrar:
    | limite de crédito | 500.00 |
    | valor autorizado  | 100.00 |
    | limite disponível | 400.00 |
    | saldo em aberto   | 0.00   |
  Quando a autorização da compra é capturada
  Então eventualmente o resumo da conta deve mostrar:
    | valor autorizado  | 0.00   |
    | limite disponível | 400.00 |
    | saldo em aberto   | 100.00 |
  Quando um pagamento de "50.00" é recebido
  Então eventualmente o resumo da conta deve mostrar:
    | valor autorizado  | 0.00  |
    | limite disponível | 450.00 |
    | saldo em aberto   | 50.00 |
```

### Business Rules

File: `credit-account-business-rules.feature`

Initial scenarios:

- authorizing a purchase above the available limit returns an error;
- receiving a payment greater than the outstanding balance returns an error;
- repeating a command with the same `Idempotency-Key` returns the same result;
- reusing the same `Idempotency-Key` with a different payload returns a conflict, if this behavior is already implemented by the application.

### Projection Consistency

File: `credit-account-consistency.feature`

Initial scenarios:

- querying with a `minVersion` greater than the current projected version returns `202 Accepted`;
- querying eventually with the expected version returns `200 OK`;
- the projection-not-ready response includes the required version and any other currently exposed diagnostic fields.

## Execution Model

The preferred long-term model is a dedicated Gradle task:

```bash
./gradlew acceptanceTest
```

The suite can initially be wired into `./gradlew test` if that is simpler, but the design target is to separate acceptance tests from lower-level technical tests. Once stable, `acceptanceTest` should be included in `check` or in the CI gate for completed tasks.

## Scenario Isolation

Each scenario must be independent.

Isolation rules:

- generate unique IDs and idempotency keys per scenario;
- do not depend on scenario execution order;
- avoid database cleanup as the default isolation mechanism;
- when list endpoints are used, assert against IDs created by the current scenario;
- keep scenario state in a scenario-scoped context.

The append-only event store and UUID-based aggregate IDs make unique test data a good initial isolation strategy.

## Success Criteria

The strategy is successful when:

- the Cucumber scenarios are understandable to someone familiar with the credit account domain;
- the suite starts Spring Boot and PostgreSQL without manual setup;
- step definitions interact only with the application through HTTP;
- asynchronous projection is observed through HTTP polling, not forced through internal calls;
- regressions in the main lifecycle, idempotency, key business rules, or projection consistency fail the suite;
- the suite can run locally and in CI with a single Gradle command.

## Open Implementation Checks

Before implementation, verify:

- whether scheduling is enabled in the application context for `@Scheduled` projection execution;
- whether the current command responses expose enough version information to drive `minVersion` polling cleanly;
- the exact error response shape for domain rule violations and idempotency conflicts;
- whether a dedicated `acceptanceTest` source set is worth creating immediately or should be introduced after the first scenarios are working in `src/test`.
