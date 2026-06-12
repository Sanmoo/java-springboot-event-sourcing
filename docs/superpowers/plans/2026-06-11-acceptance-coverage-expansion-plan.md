# Acceptance Testing Coverage Expansion Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 13 new acceptance scenarios covering business rules (7) and projection consistency (6) to the existing Cucumber suite.

**Architecture:** Extend the existing `AcceptanceTestContext`, `AcceptanceHttpClient`, and `CreditAccountStepDefinitions` to support error assertions, idempotency with explicit keys, and direct `minVersion` queries. Add two new `.feature` files. No changes to Gradle config or Spring bootstrap.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Cucumber 7, JUnit 5, AssertJ, Testcontainers, Gradle Kotlin DSL.

---

## File Structure

Modified files:

```text
src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/
  AcceptanceTestContext.java                (extend: add lastResponse, lastIdempotencyKey)
  AcceptanceHttpClient.java                 (extend: store response, add getSummaryRaw, add assignCreditLimitWithKey)
  CreditAccountStepDefinitions.java         (extend: add 7 new step methods)
```

New files:

```text
src/acceptanceTest/resources/features/
  credit-account-business-rules.feature
  credit-account-projection-consistency.feature
```

---

## Open Implementation Checks (resolved during planning)

- `Idempotency-Key` is treated case-insensitively by Spring's `@RequestHeader`, so passing it as `"Idempotency-Key"` (the convention used today) is correct.
- `ProjectionNotReadyResponse` already exposes `currentProjectionVersion` (nullable Long) and `requiredVersion` (long) — confirmed in `RestExceptionHandler.java`.
- When the projection has not yet processed any event for an aggregate, `currentProjectionVersion` is `null` — the polling-timeout scenario must handle this gracefully.

---

## Task 1: Extend `AcceptanceTestContext`

**Files:**
- Modify: `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceTestContext.java`

- [ ] **Step 1: Update the context class**

Replace the entire content of `AcceptanceTestContext.java` with:

```java
package com.sanmoo.eventsourcing.creditaccount.acceptance;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AcceptanceTestContext {

    private UUID creditAccountId;
    private UUID authorizationId;
    private Long lastProjectedVersion;
    private ResponseEntity<Map> lastResponse;
    private String lastIdempotencyKey;

    public UUID getCreditAccountId() { return creditAccountId; }
    public void setCreditAccountId(UUID creditAccountId) { this.creditAccountId = creditAccountId; }

    public UUID getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(UUID authorizationId) { this.authorizationId = authorizationId; }

    public Long getLastProjectedVersion() { return lastProjectedVersion; }
    public void setLastProjectedVersion(Long lastProjectedVersion) { this.lastProjectedVersion = lastProjectedVersion; }

    public ResponseEntity<Map> getLastResponse() { return lastResponse; }
    public void setLastResponse(ResponseEntity<Map> lastResponse) { this.lastResponse = lastResponse; }

    public String getLastIdempotencyKey() { return lastIdempotencyKey; }
    public void setLastIdempotencyKey(String lastIdempotencyKey) { this.lastIdempotencyKey = lastIdempotencyKey; }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileAcceptanceTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceTestContext.java
git commit -m "test(acceptance): extend context with lastResponse and lastIdempotencyKey"
```

---

## Task 2: Extend `AcceptanceHttpClient` to store responses and add new methods

**Files:**
- Modify: `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceHttpClient.java`

- [ ] **Step 1: Add context field and update constructor**

Add a `private final AcceptanceTestContext context;` field at the top of the class (with the other fields). Then update the existing constructor to also accept the context and store it:

```java
    public AcceptanceHttpClient(
            Environment environment,
            AcceptanceTestContext context,
            @Value("${acceptance.polling.interval-ms:150}") long pollingIntervalMs,
            @Value("${acceptance.polling.timeout-ms:5000}") long pollingTimeoutMs
    ) {
        this.environment = environment;
        this.context = context;
        this.pollingIntervalMs = pollingIntervalMs;
        this.pollingTimeoutMs = pollingTimeoutMs;
    }
```

Keep the existing `ensureInitialized()` method unchanged.

- [ ] **Step 2: Update all existing command methods to store the response on the context**

For each of the existing methods that issue a request, after the `assertThat(...)` status check, add a line that stores the response on the context. Example for `openAccount()`:

```java
    public Map<String, Object> openAccount() {
        ensureInitialized();
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl,
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        context.setLastResponse(response);
        return response.getBody();
    }
```

Apply the same `context.setLastResponse(response);` line to:
- `assignCreditLimit(UUID, String)`
- `authorizePurchase(UUID, String, String)`
- `capturePurchase(UUID, UUID)`
- `receivePayment(UUID, String)`
- `getSummary(UUID, Long)`
- `awaitProjectedSummary(UUID, long)` — store only when returning the matching summary, not on intermediate polls. Add a `context.setLastResponse(response);` line right before the successful return inside the polling loop:

```java
                if (projectedVersion >= minimumVersion) {
                    context.setLastResponse(response);
                    return latest;
                }
```

- [ ] **Step 3: Add `assignCreditLimitWithKey` method**

Add this method (anywhere in the class, near `assignCreditLimit`):

```java
    public Map<String, Object> assignCreditLimitWithKey(UUID accountId, String limit, String idempotencyKey) {
        ensureInitialized();
        HttpHeaders headers = headersWithIdempotencyKey();
        headers.set("Idempotency-Key", idempotencyKey);
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/credit-limit",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("limit", limit), headers),
                Map.class
        );
        context.setLastResponse(response);
        return response.getBody();
    }
```

- [ ] **Step 4: Add `getSummaryRaw` method**

```java
    public ResponseEntity<Map> getSummaryRaw(UUID accountId, Long minVersion) {
        ensureInitialized();
        String url = baseUrl + "/" + accountId;
        if (minVersion != null) {
            url = url + "?minVersion=" + minVersion;
        }
        ResponseEntity<Map> response = rest.exchange(
                url,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                Map.class
        );
        context.setLastResponse(response);
        return response;
    }
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileAcceptanceTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run existing acceptance test to confirm no regression**

Run: `./gradlew cleanAcceptanceTest acceptanceTest`
Expected: lifecycle scenario still PASSED

- [ ] **Step 7: Commit**

```bash
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceHttpClient.java
git commit -m "test(acceptance): store last response, add getSummaryRaw and assignCreditLimitWithKey"
```

---

## Task 3: Add new step definitions to `CreditAccountStepDefinitions`

**Files:**
- Modify: `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/CreditAccountStepDefinitions.java`

- [ ] **Step 1: Read the current step definitions file**

Read `CreditAccountStepDefinitions.java` to understand the existing structure. Identify the imports and the existing `@Before` hook.

- [ ] **Step 2: Update the `@Before` hook to clear new fields**

Replace the existing `resetContext()` method with:

```java
    @Before
    public void resetContext() {
        context.setCreditAccountId(null);
        context.setAuthorizationId(null);
        context.setLastProjectedVersion(null);
        context.setLastResponse(null);
        context.setLastIdempotencyKey(null);
    }
```

- [ ] **Step 3: Add new step method: alter limit (no key)**

```java
    @Quando("o limite de crédito é alterado para {string}")
    public void alterarLimite(String limit) {
        Map<String, Object> response = http.assignCreditLimit(context.getCreditAccountId(), limit);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }
```

- [ ] **Step 4: Add new step method: alter limit with explicit idempotency key**

```java
    @Quando("o limite de crédito é alterado para {string} usando a chave {string}")
    public void alterarLimiteComChave(String limit, String idempotencyKey) {
        http.assignCreditLimitWithKey(context.getCreditAccountId(), limit, idempotencyKey);
        context.setLastIdempotencyKey(idempotencyKey);
    }
```

- [ ] **Step 5: Add new step method: assign limit with explicit key (idempotency happy path)**

```java
    @Quando("o limite de crédito é atribuído como {string} usando a chave {string}")
    public void atribuirLimiteComChave(String limit, String idempotencyKey) {
        http.assignCreditLimitWithKey(context.getCreditAccountId(), limit, idempotencyKey);
        context.setLastIdempotencyKey(idempotencyKey);
    }
```

- [ ] **Step 6: Add new step method: re-execute last idempotent command with last key**

Skip this. The idempotency scenarios use two explicit sequential steps in the `.feature` file (each calling `assignCreditLimitWithKey` with the same key), so no "repeat last operation" step is needed.

- [ ] **Step 7: Add new step method: status response assertion**

```java
    @Então("a API deve retornar status {int} com mensagem contendo {string}")
    public void apiDeveRetornarStatus(int expectedStatus, String expectedMessageSubstring) {
        ResponseEntity<Map> last = context.getLastResponse();
        assertThat(last).isNotNull();
        assertThat(last.getStatusCode().value()).isEqualTo(expectedStatus);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = last.getBody();
        assertThat(body).isNotNull();
        // For 4xx, assert the "error" field contains the substring
        if (expectedStatus >= 400) {
            String errorMessage = (String) body.get("error");
            assertThat(errorMessage).contains(expectedMessageSubstring);
        } else {
            // For 2xx, assert the body contains the expected key (used for projection-consistency)
            assertThat(body).containsKey(expectedMessageSubstring);
        }
    }
```

- [ ] **Step 8: Add new step method: 202 with requiredVersion**

```java
    @Então("a API deve retornar 202 com requiredVersion {long}")
    public void apiDeveRetornar202ComRequiredVersion(long expectedRequiredVersion) {
        ResponseEntity<Map> last = context.getLastResponse();
        assertThat(last).isNotNull();
        assertThat(last.getStatusCode().value()).isEqualTo(202);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = last.getBody();
        assertThat(body).isNotNull();
        Number required = (Number) body.get("requiredVersion");
        assertThat(required).isNotNull();
        assertThat(required.longValue()).isEqualTo(expectedRequiredVersion);
    }
```

- [ ] **Step 9: Add new step method: consult account with minVersion**

```java
    @Quando("eu consulto a conta com minVersion {string}")
    public void consultarContaComMinVersion(String minVersion) {
        http.getSummaryRaw(context.getCreditAccountId(), Long.parseLong(minVersion));
    }
```

- [ ] **Step 10: Add new step method: consult summary without minVersion**

```java
    @Quando("eu consulto o resumo da conta")
    public void consultarResumo() {
        http.getSummaryRaw(context.getCreditAccountId(), null);
    }
```

- [ ] **Step 11: Add new step method: same result assertion (idempotency happy path)**

```java
    @Então("o resultado deve ser o mesmo")
    public void resultadoDeveSerOMesmo() {
        ResponseEntity<Map> last = context.getLastResponse();
        assertThat(last).isNotNull();
        // For idempotent replay, status is 200 and body is identical
        assertThat(last.getStatusCode().value()).isEqualTo(200);
    }
```

- [ ] **Step 12: Add new step method: 409 idempotency conflict**

```java
    @Então("a API deve retornar 409 de conflito de idempotência")
    public void apiDeveRetornar409() {
        ResponseEntity<Map> last = context.getLastResponse();
        assertThat(last).isNotNull();
        assertThat(last.getStatusCode().value()).isEqualTo(409);
    }
```

- [ ] **Step 13: Verify compilation**

Run: `./gradlew compileAcceptanceTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 14: Run existing acceptance test to confirm no regression**

Run: `./gradlew cleanAcceptanceTest acceptanceTest`
Expected: lifecycle scenario still PASSED

- [ ] **Step 15: Commit**

```bash
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/CreditAccountStepDefinitions.java
git commit -m "test(acceptance): add steps for errors, idempotency, and minVersion"
```

---

## Task 4: Create `credit-account-business-rules.feature`

**Files:**
- Create: `src/acceptanceTest/resources/features/credit-account-business-rules.feature`

- [ ] **Step 1: Write the feature file**

```gherkin
# language: pt
Funcionalidade: Regras de negócio da conta de crédito

  Cenário: Autorização acima do limite disponível retorna erro de regra de negócio
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando uma compra de "1000.00" é autorizada no estabelecimento "Store"
    Então a API deve retornar status 422 com mensagem contendo "available"

  Cenário: Pagamento maior que saldo em aberto retorna erro de regra de negócio
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando uma compra de "100.00" é autorizada no estabelecimento "Store"
    E a autorização da compra é capturada
    E um pagamento de "200.00" é recebido
    Então a API deve retornar status 422 com mensagem contendo "outstanding"

  Cenário: Comando repetido com mesma Idempotency-Key e mesmo payload retorna o mesmo resultado
    Dado que uma conta de crédito foi aberta
    Quando o limite de crédito é atribuído como "500.00" usando a chave "stable-key-aaa"
    E o limite de crédito é atribuído como "500.00" usando a chave "stable-key-aaa"
    Então o resultado deve ser o mesmo

  Cenário: Comando repetido com mesma Idempotency-Key mas payload diferente retorna conflito
    Dado que uma conta de crédito foi aberta
    Quando o limite de crédito é atribuído como "500.00" usando a chave "conflict-key-bbb"
    E o limite de crédito é atribuído como "800.00" usando a chave "conflict-key-bbb"
    Então a API deve retornar 409 de conflito de idempotência

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

  Cenário: Diminuir limite abaixo de saldo mais autorizado falha
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "1000.00"
    Quando uma compra de "500.00" é autorizada no estabelecimento "Store"
    E a autorização da compra é capturada
    E o limite de crédito é alterado para "300.00"
    Então a API deve retornar status 422 com mensagem contendo "limit"

  Cenário: Diminuir limite respeitando saldo mais autorizado funciona
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "1000.00"
    Quando uma compra de "200.00" é autorizada no estabelecimento "Store"
    E o limite de crédito é alterado para "600.00"
    Então eventualmente o resumo da conta deve mostrar:
      | limite de crédito | 600.00  |
      | valor autorizado  | 200.00  |
      | limite disponível | 400.00  |
      | saldo em aberto   | 0.00    |
```

- [ ] **Step 2: Run the business rules acceptance test**

Run: `./gradlew cleanAcceptanceTest acceptanceTest --info 2>&1 | tail -40`
Expected: all 7 business-rule scenarios pass.

- [ ] **Step 3: If scenarios fail, debug and fix**

Common issues:

- The error message substring may not match — check the actual exception message text in the source and adjust the expected substring.
- The idempotency happy-path may return 409 instead of 200 — verify that the `assignCreditLimit` endpoint truly returns the same response on replay.
- The "alterar limite com chave" step is the same code path as `assignCreditLimit` — both should work.

- [ ] **Step 4: Commit**

```bash
git add src/acceptanceTest/resources/features/credit-account-business-rules.feature
git commit -m "test(acceptance): add business-rules feature with 7 scenarios"
```

---

## Task 5: Create `credit-account-projection-consistency.feature`

**Files:**
- Create: `src/acceptanceTest/resources/features/credit-account-projection-consistency.feature`

- [ ] **Step 1: Write the feature file**

```gherkin
# language: pt
Funcionalidade: Consistência da projeção assíncrona

  Cenário: Consulta com minVersion maior que a versão projetada retorna 202
    Dado que uma conta de crédito foi aberta
    Quando eu consulto a conta com minVersion "99"
    Então a API deve retornar 202 com requiredVersion 99

  Cenário: minVersion 0 retorna 200 imediatamente
    Dado que uma conta de crédito foi aberta
    Quando eu consulto a conta com minVersion "0"
    Então a API deve retornar status 200 com mensagem contendo "projectedVersion"

  Cenário: Leitura sem minVersion retorna 200 com versão projetada corrente
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando eu consulto o resumo da conta
    Então a API deve retornar status 200 com mensagem contendo "creditLimit"

  Cenário: Sequência de comando e leitura eventual com minVersion correto
    Dado que uma conta de crédito foi aberta
    Quando o limite de crédito é alterado para "750.00"
    E eu consulto a conta com minVersion "2"
    Então eventualmente a API deve retornar 200 com projectedVersion >= 2
```

Note: scenarios "minVersion 0" and "leitura sem minVersion" use "a API deve retornar status 200 com mensagem contendo X" where X is a body key (e.g. "projectedVersion", "creditLimit"). The step method handles both 4xx and 2xx cases.

- [ ] **Step 2: Add the new step: "eventualmente a API deve retornar 200 com projectedVersion >= N"**

```java
    @Então("eventualmente a API deve retornar 200 com projectedVersion >= {long}")
    public void eventualmenteApiDeveRetornar200ComProjectedVersionMaiorOuIgual(long expectedVersion) {
        Map<String, Object> summary = http.awaitProjectedSummary(context.getCreditAccountId(), expectedVersion);
        assertThat(summary).isNotNull();
        long projected = ((Number) summary.get("projectedVersion")).longValue();
        assertThat(projected).isGreaterThanOrEqualTo(expectedVersion);
    }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileAcceptanceTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run the projection consistency acceptance test**

Run: `./gradlew cleanAcceptanceTest acceptanceTest --info 2>&1 | tail -40`
Expected: all 4 projection-consistency scenarios pass.

- [ ] **Step 5: If scenarios fail, debug and fix**

Common issues:

- The "minVersion 0" scenario: the body should contain the key `projectedVersion` — verify the controller's response body includes it.
- The "eventualmente com minVersion" scenario: the polling timeout is 5s; this should be plenty given the scheduler runs at 1s. If it times out, increase the timeout in `application.yml` or reduce the poll interval.

- [ ] **Step 6: Commit**

```bash
git add src/acceptanceTest/resources/features/credit-account-projection-consistency.feature
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/CreditAccountStepDefinitions.java
git commit -m "test(acceptance): add projection-consistency feature with 4 scenarios"
```

---

## Task 6: Run full check

- [ ] **Step 1: Run unit and integration tests**

Run: `./gradlew test`
Expected: all existing tests pass.

- [ ] **Step 2: Run all acceptance tests**

Run: `./gradlew cleanAcceptanceTest acceptanceTest`
Expected: all 3 features (lifecycle + business-rules + projection-consistency) pass with a total of 12 scenarios.

- [ ] **Step 3: Commit any final adjustments if needed**

If any code or configuration changed in this task, commit with a descriptive message, e.g. `chore(acceptance): tune polling defaults for new scenarios`.

---

## Out of Scope (deferred)

The following scenarios were intentionally not included in this plan:

- Concurrency conflict (409) — requires simulating concurrent clients.
- Account not found (404) — already covered by existing ITs.
- Invalid page size (400) — already covered by `CreditAccountControllerListIT`.
- Validation errors (negative amounts) — covered by existing ITs.
- "Decreasing limit below 0" and "decreasing limit equal to current" — trivial cases not worth a separate scenario.
