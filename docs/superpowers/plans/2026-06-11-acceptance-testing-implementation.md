# Acceptance Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a Cucumber-based acceptance suite for the credit account event-sourced API, validating the main lifecycle scenario against the real HTTP API and observing asynchronous projection only through HTTP polling.

**Architecture:** A new Gradle `acceptanceTest` source set holds Cucumber feature files, JUnit runner, step definitions, a typed HTTP test client, and a per-scenario state holder. The suite uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` and the existing Testcontainers PostgreSQL. Scenarios poll `GET /credit-accounts/{id}?minVersion=N` until the projection reaches the expected version, never calling internal components such as `ProjectionWorker`.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Cucumber 7 (JUnit Platform), JUnit 5, AssertJ, Testcontainers, Gradle Kotlin DSL.

---

## Open Implementation Checks (resolved during planning)

- `@EnableScheduling` is present in `com.sanmoo.eventsourcing.creditaccount.CreditAccountApplication`. The scheduled projection worker is active during the test.
- Command responses embed `projectedVersion` inside `CreditAccountOutput`, returned in the response body. The HTTP client reads it from the JSON body.
- The acceptance suite uses a dedicated `acceptanceTest` Gradle source set from the start to keep the suite conceptually separate.

---

## File Structure

New files:

```text
build.gradle.kts                                      (modify)
src/acceptanceTest/resources/features/credit-account-lifecycle.feature
src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/CucumberAcceptanceTest.java
src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceSpringConfig.java
src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceTestContext.java
src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceHttpClient.java
src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/CreditAccountStepDefinitions.java
```

---

## Task 1: Add Cucumber dependencies

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add Cucumber dependencies to `build.gradle.kts`**

Add inside the `dependencies { }` block:

```kotlin
    add("acceptanceTestImplementation", "io.cucumber:cucumber-java:7.20.1")
    add("acceptanceTestImplementation", "io.cucumber:cucumber-junit-platform-engine:7.20.1")
    add("acceptanceTestImplementation", "io.cucumber:cucumber-spring:7.20.1")
```

- [ ] **Step 2: Verify Gradle resolves dependencies**

Run: `./gradlew dependencies --configuration acceptanceTestRuntimeClasspath 2>&1 | tail -20`
Expected: lists cucumber artifacts without unresolved errors.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build(acceptance): add cucumber dependencies"
```

---

## Task 2: Create the `acceptanceTest` source set and Gradle task

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add the source set declaration**

Inside `build.gradle.kts`, add after the existing `sourceSets { ... }` block:

```kotlin
sourceSets {
    create("acceptanceTest") {
        java {
            compileClasspath += sourceSets.main.get().output
            compileClasspath += sourceSets.test.get().output
            runtimeClasspath += sourceSets.main.get().output
            runtimeClasspath += sourceSets.test.get().output
        }
        resources {
            compileClasspath += sourceSets.test.get().output
            runtimeClasspath += sourceSets.test.get().output
        }
    }
}
```

- [ ] **Step 2: Extend the acceptance test configurations from the main test configurations**

Add inside `configurations { }`:

```kotlin
    named("acceptanceTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    named("acceptanceTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
```

- [ ] **Step 3: Add the `acceptanceTest` Gradle task**

Add at the end of the file:

```kotlin
tasks.register<Test>("acceptanceTest") {
    description = "Runs the Cucumber acceptance suite against the real Spring Boot application"
    group = "verification"
    testClassesDirs = sourceSets["acceptanceTest"].output.classesDirs
    classpath = sourceSets["acceptanceTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    systemProperty("spring.profiles.active", "test")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
```

- [ ] **Step 4: Verify the task is registered**

Run: `./gradlew tasks --group verification`
Expected: `acceptanceTest` appears in the list.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts
git commit -m "build(acceptance): add acceptanceTest source set and task"
```

---

## Task 3: Create the lifecycle feature file

**Files:**
- Create: `src/acceptanceTest/resources/features/credit-account-lifecycle.feature`

- [ ] **Step 1: Write the feature file**

```gherkin
# language: pt
Funcionalidade: Ciclo de vida de uma conta de crédito

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
      | valor autorizado  | 0.00   |
      | limite disponível | 450.00 |
      | saldo em aberto   | 50.00  |
```

- [ ] **Step 2: Commit**

```bash
git add src/acceptanceTest/resources/features/credit-account-lifecycle.feature
git commit -m "test(acceptance): add credit account lifecycle feature"
```

---

## Task 4: Create the Spring bootstrap for acceptance tests

**Files:**
- Create: `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceSpringConfig.java`

- [ ] **Step 1: Write the Spring config class**

This class boots the Spring Boot application with Testcontainers and is imported by the step glue. cucumber-spring will pick it up via component scanning of the acceptance package.

```java
package com.sanmoo.eventsourcing.creditaccount.acceptance;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AcceptanceSpringConfig {
}
```

- [ ] **Step 2: Commit**

```bash
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceSpringConfig.java
git commit -m "test(acceptance): add Spring bootstrap for acceptance suite"
```

---

## Task 5: Create the per-scenario state holder

**Files:**
- Create: `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceTestContext.java`

- [ ] **Step 1: Write the context class**

Cucumber with cucumber-spring creates a fresh Spring context per scenario, so a singleton component is already scenario-isolated. The `CreditAccountStepDefinitions.@Before` hook clears the values at the start of each scenario as a defensive reset.

```java
package com.sanmoo.eventsourcing.creditaccount.acceptance;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AcceptanceTestContext {

    private UUID creditAccountId;
    private UUID authorizationId;
    private Long lastProjectedVersion;

    public UUID getCreditAccountId() { return creditAccountId; }
    public void setCreditAccountId(UUID creditAccountId) { this.creditAccountId = creditAccountId; }

    public UUID getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(UUID authorizationId) { this.authorizationId = authorizationId; }

    public Long getLastProjectedVersion() { return lastProjectedVersion; }
    public void setLastProjectedVersion(Long lastProjectedVersion) { this.lastProjectedVersion = lastProjectedVersion; }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceTestContext.java
git commit -m "test(acceptance): add per-scenario state holder"
```

---

## Task 6: Create the typed HTTP test client with polling

**Files:**
- Create: `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceHttpClient.java`

- [ ] **Step 1: Write the HTTP client**

```java
package com.sanmoo.eventsourcing.creditaccount.acceptance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Component
public class AcceptanceHttpClient {

    private final TestRestTemplate rest;
    private final String baseUrl;
    private final Duration pollingInterval;
    private final Duration pollingTimeout;

    public AcceptanceHttpClient(
            TestRestTemplate rest,
            @Value("${local.server.port}") int port,
            @Value("${acceptance.polling.interval-ms:150}") long pollingIntervalMs,
            @Value("${acceptance.polling.timeout-ms:5000}") long pollingTimeoutMs
    ) {
        this.rest = rest;
        this.baseUrl = "http://localhost:" + port + "/credit-accounts";
        this.pollingInterval = Duration.ofMillis(pollingIntervalMs);
        this.pollingTimeout = Duration.ofMillis(pollingTimeoutMs);
    }

    public Map<String, Object> openAccount() {
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl,
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    public Map<String, Object> assignCreditLimit(UUID accountId, String limit) {
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/credit-limit",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("limit", limit), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    public Map<String, Object> authorizePurchase(UUID accountId, String amount, String merchantName) {
        UUID authorizationId = UUID.randomUUID();
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/purchases/authorizations",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "authorizationId", authorizationId.toString(),
                        "amount", amount,
                        "merchantName", merchantName
                ), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("authorizationId", authorizationId.toString());
        return response.getBody();
    }

    public Map<String, Object> capturePurchase(UUID accountId, UUID authorizationId) {
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/purchases/authorizations/" + authorizationId + "/capture",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    public Map<String, Object> receivePayment(UUID accountId, String amount) {
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", amount), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    public Map<String, Object> getSummary(UUID accountId, Long minVersion) {
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
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    public Map<String, Object> awaitProjectedSummary(UUID accountId, long minimumVersion) {
        Instant deadline = Instant.now().plus(pollingTimeout);
        Map<String, Object> latest = null;
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<Map> response = rest.exchange(
                    baseUrl + "/" + accountId + "?minVersion=" + minimumVersion,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    Map.class
            );
            if (response.getStatusCode() == HttpStatus.OK) {
                latest = response.getBody();
                long projectedVersion = ((Number) latest.get("projectedVersion")).longValue();
                if (projectedVersion >= minimumVersion) {
                    return latest;
                }
            }
            sleep();
        }
        throw new AssertionError(
                "Timed out waiting for projected version >= " + minimumVersion
                        + " for account " + accountId + "; last response: " + latest
        );
    }

    private void sleep() {
        try {
            Thread.sleep(pollingInterval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private HttpHeaders headersWithIdempotencyKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return headers;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/AcceptanceHttpClient.java
git commit -m "test(acceptance): add HTTP client with HTTP-only polling"
```

---

## Task 7: Create the step definitions

**Files:**
- Create: `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/CreditAccountStepDefinitions.java`

- [ ] **Step 1: Write the step definitions**

```java
package com.sanmoo.eventsourcing.creditaccount.acceptance;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CreditAccountStepDefinitions {

    @Autowired
    private AcceptanceHttpClient http;

    @Autowired
    private AcceptanceTestContext context;

    @Before
    public void resetContext() {
        context.setCreditAccountId(null);
        context.setAuthorizationId(null);
        context.setLastProjectedVersion(null);
    }

    @Dado("que uma conta de crédito foi aberta")
    public void abrirConta() {
        Map<String, Object> response = http.openAccount();
        UUID accountId = UUID.fromString((String) response.get("creditAccountId"));
        context.setCreditAccountId(accountId);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @E("o limite de crédito da conta é {string}")
    public void definirLimite(String limit) {
        Map<String, Object> response = http.assignCreditLimit(context.getCreditAccountId(), limit);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @Quando("uma compra de {string} é autorizada no estabelecimento {string}")
    public void autorizarCompra(String amount, String merchantName) {
        Map<String, Object> response = http.authorizePurchase(context.getCreditAccountId(), amount, merchantName);
        UUID authorizationId = UUID.fromString((String) response.get("authorizationId"));
        context.setAuthorizationId(authorizationId);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @Quando("a autorização da compra é capturada")
    public void capturarCompra() {
        Map<String, Object> response = http.capturePurchase(context.getCreditAccountId(), context.getAuthorizationId());
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @Quando("um pagamento de {string} é recebido")
    public void receberPagamento(String amount) {
        Map<String, Object> response = http.receivePayment(context.getCreditAccountId(), amount);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @Então("eventualmente o resumo da conta deve mostrar:")
    public void resumirConta(DataTable dataTable) {
        Map<String, String> expected = dataTable.asMap(String.class, String.class);
        Map<String, Object> summary = http.awaitProjectedSummary(
                context.getCreditAccountId(),
                context.getLastProjectedVersion()
        );

        assertThat((String) summary.get("creditLimit")).isEqualTo(expected.get("limite de crédito"));
        assertThat((String) summary.get("authorizedAmount")).isEqualTo(expected.get("valor autorizado"));
        assertThat((String) summary.get("availableLimit")).isEqualTo(expected.get("limite disponível"));
        assertThat((String) summary.get("outstandingBalance")).isEqualTo(expected.get("saldo em aberto"));
    }
}
```

Note: `assertThat((String) ...)` returns a `StringAssert` (via AssertJ's overload for `String`), enabling the chained `.isEqualTo(...)` calls. If the API ever normalizes money formatting differently from the Gherkin strings, add a `toMoneyString(String)` helper here.

- [ ] **Step 2: Commit**

```bash
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/CreditAccountStepDefinitions.java
git commit -m "test(acceptance): add step definitions for lifecycle scenario"
```

---

## Task 8: Create the Cucumber runner

**Files:**
- Create: `src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/CucumberAcceptanceTest.java`

- [ ] **Step 1: Write the runner**

```java
package com.sanmoo.eventsourcing.creditaccount.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sanmoo.eventsourcing.creditaccount.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,html:build/reports/cucumber.html")
public class CucumberAcceptanceTest {
}
```

- [ ] **Step 2: Run the acceptance suite**

Run: `./gradlew acceptanceTest --info 2>&1 | tail -80`
Expected: the lifecycle scenario passes. HTML report generated at `build/reports/cucumber.html`.

- [ ] **Step 3: If the scenario fails, inspect and fix**

Common issues:

- Cucumber cannot find steps: verify the GLUE package matches.
- `projectedVersion` missing in the body: verify the controller includes the field. The current `CreditAccountController.toMap(...)` already puts `projectedVersion` into the response.
- Polling times out: inspect logs; the scheduler is active, so this should be sub-second per poll. Consider raising `acceptance.polling.timeout-ms` if the CI is slow.

- [ ] **Step 4: Commit**

```bash
git add src/acceptanceTest/java/com/sanmoo/eventsourcing/creditaccount/acceptance/CucumberAcceptanceTest.java
git commit -m "test(acceptance): add cucumber runner and verify scenario"
```

---

## Task 9: Run the full check

- [ ] **Step 1: Run unit and integration tests**

Run: `./gradlew test`
Expected: all existing tests still pass.

- [ ] **Step 2: Run the acceptance suite**

Run: `./gradlew acceptanceTest`
Expected: the lifecycle scenario passes.

- [ ] **Step 3: Commit any final adjustments if needed**

If any code or configuration changed in this task, commit with a descriptive message, e.g. `chore(acceptance): tune polling defaults for CI`.

---

## Follow-up Plans (not part of this plan)

Once the lifecycle scenario is green, the next plan should add:

- `credit-account-business-rules.feature` (insufficient limit, payment above balance, idempotency);
- `credit-account-consistency.feature` (`minVersion` returns 202 then 200).

A future plan may also add the `acceptanceTest` task to `check` once the suite is stable.
