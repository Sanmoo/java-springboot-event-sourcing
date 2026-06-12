# Acceptance Testing Coverage Expansion Design

## Context

The first round of acceptance testing (committed in `feat/acceptance-testing`) added a Cucumber-based acceptance suite for the credit account event-sourced API with one happy-path scenario (`credit-account-lifecycle.feature`). The suite is now passing, validated against the real HTTP API and observing the asynchronous projection only through HTTP polling.

The first round of design identified follow-up scenarios that were deferred:

- business rules and error responses
- projection consistency, including the `202 Accepted` and `minVersion` semantics

This spec adds those scenarios now to increase confidence in regression detection and business-rule enforcement.

## Chosen Approach

Add two new Gherkin feature files under `src/acceptanceTest/resources/features/`:

- `credit-account-business-rules.feature` — 7 scenarios covering domain rule violations, idempotency, and credit-limit transitions.
- `credit-account-projection-consistency.feature` — 6 scenarios covering `minVersion` query semantics and asynchronous projection.

Extend the existing `AcceptanceHttpClient` to:

- expose the last `ResponseEntity` so error step definitions can inspect status and body;
- accept an optional `Idempotency-Key` parameter where idempotency semantics matter.

Extend the existing `CreditAccountStepDefinitions` with new Portuguese steps:

- error assertions (`a API deve retornar erro {int} com mensagem contendo {string}`);
- idempotency steps that accept an explicit key (`usando a chave "{string}"`);
- `minVersion` consultation with explicit version;
- consultation without `minVersion`;
- 202 response validation with `requiredVersion` and `currentProjectionVersion`.

No changes to:

- `build.gradle.kts` (no new dependencies);
- `CucumberAcceptanceTest.java` (runner unchanged);
- `AcceptanceSpringConfig.java` (Spring bootstrap unchanged);
- the existing happy-path feature or its step definitions.

## File Structure

```text
src/acceptanceTest/resources/features/
  credit-account-lifecycle.feature                 (existing)
  credit-account-business-rules.feature            (new)
  credit-account-projection-consistency.feature    (new)

src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/
  AcceptanceHttpClient.java                        (extend)
  CreditAccountStepDefinitions.java                (extend)
  AcceptanceTestContext.java                       (extend: add lastResponse, lastIdempotencyKey)
```

## New Feature Files

### `credit-account-business-rules.feature`

Scenarios:

1. **Autorização acima do limite disponível** returns 422 with a domain-rule message.
2. **Pagamento maior que saldo em aberto** returns 422 with a domain-rule message.
3. **Comando repetido com mesma `Idempotency-Key` e mesmo payload** returns the same result without producing a new event.
4. **Comando repetido com mesma `Idempotency-Key` mas payload diferente** returns 409 (idempotency conflict).
5. **Aumentar limite já utilizado** (with outstanding authorizations) works and increases `availableLimit` without losing the existing authorizations.
6. **Diminuir limite abaixo de `saldo + autorizado`** fails with 422.
7. **Diminuir limite mantendo `limite ≥ saldo + autorizado`** works and the read model reflects the new limit and recomputed `availableLimit`.

Example for scenario 5:

```gherkin
Cenário: Aumentar limite com autorizações pendentes preserva as autorizações e libera mais limite
  Dado que uma conta de crédito foi aberta
  E o limite de crédito da conta é "500.00"
  Quando uma compra de "200.00" é autorizada no estabelecimento "Store"
  E o limite de crédito é alterado para "1000.00"
  Então eventualmente o resumo da conta deve mostrar:
    | limite de crédito | 1000.00 |
    | valor autorizado  | 200.00  |
    | limite disponível | 800.00  |
    | saldo em aberto   | 0.00    |
```

### `credit-account-projection-consistency.feature`

Scenarios:

1. `minVersion` greater than the current projected version returns 202 with `requiredVersion` and `currentProjectionVersion` in the body.
2. After polling, the same `minVersion` returns 200 with `projectedVersion ≥ requiredVersion`.
3. `minVersion=0` returns 200 immediately.
4. `minVersion` greater than the aggregate's final version keeps returning 202 until the polling timeout (server must not crash and must respond consistently).
5. Reading the summary without `minVersion` returns 200 with the current projected version.
6. Sequencing — a command followed by an immediate read without `minVersion` may return a stale version; a read with `minVersion` set to the expected version eventually returns 200.

Example for scenario 1:

```gherkin
Cenário: Consulta com minVersion maior que a versão projetada retorna 202
  Dado que uma conta de crédito foi aberta
  Quando eu consulto a conta com minVersion "99"
  Então a API deve retornar 202 com requiredVersion 99
```

## New and Updated Step Definitions

### New steps in `CreditAccountStepDefinitions`

| Step | Purpose |
|---|---|
| `@Quando("o limite de crédito é alterado para {string}")` | Calls the same `assignCreditLimit` endpoint — clarifies intent in business-rule scenarios. |
| `@Quando("o limite de crédito é alterado para {string} usando a chave {string}")` | Calls the endpoint with an explicit `Idempotency-Key` for idempotency scenarios. |
| `@Quando("eu consulto a conta com minVersion {string}")` | Calls `GET /credit-accounts/{id}?minVersion=N` and stores the response. |
| `@Quando("eu consulto o resumo da conta")` | Calls `GET /credit-accounts/{id}` without `minVersion`. |
| `@Então("a API deve retornar erro {int} com mensagem contendo {string}")` | Asserts the last response status and message substring. |
| `@Então("a API deve retornar 202 com requiredVersion {long}")` | Asserts the 202 body contains the expected `requiredVersion` and a `currentProjectionVersion`. |
| `@Então("eventualmente a API deve retornar 200 com projectedVersion >= {long}")` | Polls until 200 with `projectedVersion ≥ N`, reusing the existing polling mechanism. |

### Updates to `AcceptanceHttpClient`

- Each command method continues to return the `Map<String, Object>` body for happy-path code, but also stores the raw `ResponseEntity<Map>` (status + body) on `AcceptanceTestContext` so error step definitions can inspect it.
- A new `getSummaryRaw(UUID accountId, Long minVersion)` returns the raw `ResponseEntity<Map>` directly so steps can assert 200 vs 202 without going through polling.
- A new `assignCreditLimitWithKey(UUID accountId, String limit, String idempotencyKey)` accepts an explicit `Idempotency-Key` for idempotency scenarios.

### Updates to `AcceptanceTestContext`

Add fields:
- `ResponseEntity<Map> lastResponse`
- `String lastIdempotencyKey` (for scenarios that need to reuse a key)

The `@Before` hook clears these along with the existing fields.

## Execution Model

- `./gradlew acceptanceTest` runs all `.feature` files.
- No changes to `build.gradle.kts` or to `CucumberAcceptanceTest.java`.
- The lifecycle scenario continues to pass alongside the new scenarios.

## Scenario Isolation

Same strategy as the first round:

- Each scenario generates its own UUIDs.
- The state holder is per-scenario because cucumber-spring creates a fresh Spring context per scenario.
- The `@Before` hook clears all fields.
- For idempotency scenarios that need to share a key, the key is passed as a Gherkin argument and stored in `AcceptanceTestContext.lastIdempotencyKey` so the second step can reuse it.

## Success Criteria

The new scenarios are considered adequate when:

- the 7 business-rule scenarios pass;
- the 6 projection-consistency scenarios pass;
- the idempotency-with-different-payload scenario actually triggers `IdempotencyConflictException` and returns 409;
- `minVersion` greater than the final aggregate version returns 202 with correct diagnostic fields;
- no step definition calls `ProjectionWorker` or any other internal component;
- the existing happy-path scenario continues to pass;
- `./gradlew acceptanceTest` and `./gradlew test` both pass.

## Out of Scope for This Round

The following are intentionally deferred:

- `ConcurrencyConflictException` (409) scenarios — would require simulating concurrent clients, which needs additional infrastructure.
- `AccountNotFoundException` (404) scenarios — low regression-detection value; the existing IT already covers the path.
- `InvalidPageSizeException` (400) scenarios — already covered by the `CreditAccountControllerListIT`.
- Validation-level errors (e.g., negative amounts) — covered by existing controller ITs and not part of the domain's event-sourcing behavior.

## Open Implementation Checks

To confirm during implementation:

- `Idempotency-Key` header case sensitivity — Cucumber HTTP calls send it as written; verify Spring's `RequestHeader` binding is case-insensitive (default behavior).
- Whether the `requiredVersion` field returned in `ProjectionNotReadyResponse` is exposed in the JSON response — already verified in the controller test, but double-check the field name in the response body matches `requiredVersion`.
- Whether `currentProjectionVersion` is `null` when the projection has not yet processed any event — this is a real edge case the scenario for "polling timeout" will exercise.
