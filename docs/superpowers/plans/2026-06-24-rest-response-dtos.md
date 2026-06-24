# REST Response DTOs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all `Map<String, Object>` and `Map<String, String>` payloads in the REST adapter with explicit Java records, keeping the JSON contract unchanged.

**Architecture:** Introduce typed response records (`CreditAccountResponse`, `AuthorizationResponse`, `AuthorizePurchaseResponse`, `ErrorResponse`) and a pure mapper class (`CreditAccountDtoMapper`) in the `adapter.in.rest.dto` package. Update `CreditAccountController`, `PageResponse`, and `RestExceptionHandler` to use them, then migrate integration tests to the new types.

**Tech Stack:** Java 21, Spring Boot 3, Maven, JUnit 5, RestTemplate.

## Global Constraints

- Do not change request DTOs or use case logic.
- Keep JSON response shapes identical to the current implementation.
- Prefer `record` for DTOs, following existing request DTO style.
- Use static pure mapper methods; no Spring component required for mapping.
- Commit after each task.

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/.../adapter/in/rest/dto/CreditAccountResponse.java` | Standard account response shape. |
| `src/main/java/.../adapter/in/rest/dto/AuthorizationResponse.java` | Authorization item inside account response. |
| `src/main/java/.../adapter/in/rest/dto/AuthorizePurchaseResponse.java` | Wrapper for authorize-purchase response (account + authorizationId). |
| `src/main/java/.../adapter/in/rest/dto/ErrorResponse.java` | Standard error payload. |
| `src/main/java/.../adapter/in/rest/dto/CreditAccountDtoMapper.java` | Converts `CreditAccountOutput` → `CreditAccountResponse` and `PurchaseAuthorizationOutput` → `AuthorizationResponse`. |
| `src/main/java/.../adapter/in/rest/dto/PageResponse.java` | Paged response with typed items. |
| `src/main/java/.../adapter/in/rest/CreditAccountController.java` | Returns typed response entities. |
| `src/main/java/.../adapter/in/rest/RestExceptionHandler.java` | Returns typed error responses. |
| `src/test/java/.../adapter/in/rest/CreditAccountControllerIT.java` | Uses typed DTOs instead of `Map.class`. |
| `src/test/java/.../adapter/in/rest/CreditAccountControllerListIT.java` | Uses typed DTOs instead of `Map.class`. |

---

### Task 1: Create Response DTOs and Mapper

**Files:**

- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/CreditAccountResponse.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/AuthorizationResponse.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/AuthorizePurchaseResponse.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/ErrorResponse.java`
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/CreditAccountDtoMapper.java`

**Interfaces:**

- Consumes: `CreditAccountOutput` (fields: `creditAccountId`, `opened`, `creditLimit`, `outstandingBalance`, `authorizedAmount`, `availableLimit`, `projectedVersion`, `authorizations`), `PurchaseAuthorizationOutput` (fields: `authorizationId`, `amount`, `status`, `merchantName`).
- Produces: `CreditAccountResponse`, `AuthorizationResponse`, `AuthorizePurchaseResponse`, `ErrorResponse`, static methods `CreditAccountDtoMapper.toResponse(CreditAccountOutput)` and `CreditAccountDtoMapper.toResponse(PurchaseAuthorizationOutput)`.

- [ ] **Step 1: Create `AuthorizationResponse.java`**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

public record AuthorizationResponse(
        String authorizationId,
        String amount,
        String status,
        String merchantName
) {
}
```

- [ ] **Step 2: Create `CreditAccountResponse.java`**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.util.List;

public record CreditAccountResponse(
        String creditAccountId,
        boolean opened,
        String creditLimit,
        String outstandingBalance,
        String authorizedAmount,
        String availableLimit,
        Long projectedVersion,
        List<AuthorizationResponse> authorizations
) {
}
```

- [ ] **Step 3: Create `AuthorizePurchaseResponse.java`**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

public record AuthorizePurchaseResponse(
        CreditAccountResponse account,
        String authorizationId
) {
}
```

- [ ] **Step 4: Create `ErrorResponse.java`**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

public record ErrorResponse(
        String error
) {
}
```

- [ ] **Step 5: Create `CreditAccountDtoMapper.java`**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.PurchaseAuthorizationOutput;

import java.util.List;

public final class CreditAccountDtoMapper {

    private CreditAccountDtoMapper() {
    }

    public static CreditAccountResponse toResponse(CreditAccountOutput output) {
        return new CreditAccountResponse(
                output.creditAccountId(),
                output.opened(),
                output.creditLimit(),
                output.outstandingBalance(),
                output.authorizedAmount(),
                output.availableLimit(),
                output.projectedVersion(),
                output.authorizations().stream()
                        .map(CreditAccountDtoMapper::toResponse)
                        .toList()
        );
    }

    public static AuthorizationResponse toResponse(PurchaseAuthorizationOutput output) {
        return new AuthorizationResponse(
                output.authorizationId(),
                output.amount(),
                output.status(),
                output.merchantName()
        );
    }
}
```

- [ ] **Step 6: Compile to verify**.

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**.

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/CreditAccountResponse.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/AuthorizationResponse.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/AuthorizePurchaseResponse.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/ErrorResponse.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/CreditAccountDtoMapper.java
git commit -m "feat: add explicit REST response DTOs and mapper"
```

---

### Task 2: Update `PageResponse` to Use Typed Items

**Files:**

- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/PageResponse.java`

**Interfaces:**

- Consumes: `CreditAccountResponse`.
- Produces: `PageResponse` with `List<CreditAccountResponse> items`.

- [ ] **Step 1: Replace `Map` with `CreditAccountResponse` in `PageResponse`.**

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto;

import java.util.List;

public record PageResponse(
        List<CreditAccountResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
```

- [ ] **Step 2: Compile to verify**.

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**.

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/dto/PageResponse.java
git commit -m "refactor: use typed CreditAccountResponse in PageResponse"
```

---

### Task 3: Refactor `CreditAccountController`

**Files:**

- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`

**Interfaces:**

- Consumes: `CreditAccountDtoMapper.toResponse(...)`, `AuthorizePurchaseResponse`, `PageResponse`.
- Produces: Controller methods returning typed `ResponseEntity<...>`.

- [ ] **Step 1: Update imports and method signatures.**

Remove:

- `java.util.LinkedHashMap`
- `java.util.Map`
- The private `toMap(CreditAccountOutput)` method.

Add imports for the new DTOs and mapper.

- [ ] **Step 2: Rewrite each endpoint to return typed DTOs.**

Full file content:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.AuthorizePurchaseResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.CreditAccountDtoMapper;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.CreditAccountResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.PageResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.*;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.*;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.*;
import com.sanmoo.eventsourcing.creditaccount.domain.error.CreditLimitAlreadyAssignedException;
import com.sanmoo.eventsourcing.creditaccount.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/credit-accounts")
@RequiredArgsConstructor
public class CreditAccountController {

    private final OpenCreditAccountUseCase openCreditAccountUseCase;
    private final AssignCreditLimitUseCase assignCreditLimitUseCase;
    private final ChangeCreditLimitUseCase changeCreditLimitUseCase;
    private final AuthorizePurchaseUseCase authorizePurchaseUseCase;
    private final CapturePurchaseUseCase capturePurchaseUseCase;
    private final ReleasePurchaseAuthorizationUseCase releasePurchaseAuthorizationUseCase;
    private final ReceivePaymentUseCase receivePaymentUseCase;
    private final GetCreditAccountUseCase getCreditAccountUseCase;
    private final ListCreditAccountsUseCase listCreditAccountsUseCase;

    @PostMapping
    public ResponseEntity<CreditAccountResponse> openCreditAccount(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) OpenCreditAccountRequest request) {
        var input = new OpenCreditAccountInput(idempotencyKey);
        var output = openCreditAccountUseCase.execute(input);
        var response = CreditAccountDtoMapper.toResponse(output.account());
        if (output.replayed()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/credit-limit")
    public ResponseEntity<CreditAccountResponse> assignCreditLimit(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AssignCreditLimitRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        Money limit = Money.positive(request.limit());
        try {
            var input = new AssignCreditLimitInput(idempotencyKey, creditAccountId, limit);
            var output = assignCreditLimitUseCase.execute(input);
            return ResponseEntity.ok(CreditAccountDtoMapper.toResponse(output.account()));
        } catch (CreditLimitAlreadyAssignedException e) {
            var changeInput = new ChangeCreditLimitInput(idempotencyKey, creditAccountId, limit);
            var changeOutput = changeCreditLimitUseCase.execute(changeInput);
            return ResponseEntity.ok(CreditAccountDtoMapper.toResponse(changeOutput.account()));
        }
    }

    @PostMapping("/{id}/purchases/authorizations")
    public ResponseEntity<AuthorizePurchaseResponse> authorizePurchase(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizePurchaseRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authorizationId = AuthorizationId.of(request.authorizationId());
        var input = new AuthorizePurchaseInput(
                idempotencyKey, creditAccountId, authorizationId,
                Money.positive(request.amount()), request.merchantName());
        var output = authorizePurchaseUseCase.execute(input);
        var accountResponse = CreditAccountDtoMapper.toResponse(output.account());
        return ResponseEntity.ok(new AuthorizePurchaseResponse(accountResponse, output.authorizationId()));
    }

    @PostMapping("/{id}/purchases/authorizations/{authorizationId}/capture")
    public ResponseEntity<CreditAccountResponse> capturePurchase(
            @PathVariable String id,
            @PathVariable String authorizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) CapturePurchaseRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authId = AuthorizationId.of(UUID.fromString(authorizationId));
        var input = new CapturePurchaseInput(idempotencyKey, creditAccountId, authId);
        var output = capturePurchaseUseCase.execute(input);
        return ResponseEntity.ok(CreditAccountDtoMapper.toResponse(output.account()));
    }

    @PostMapping("/{id}/purchases/authorizations/{authorizationId}/release")
    public ResponseEntity<CreditAccountResponse> releasePurchaseAuthorization(
            @PathVariable String id,
            @PathVariable String authorizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) ReleasePurchaseAuthorizationRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authId = AuthorizationId.of(UUID.fromString(authorizationId));
        var input = new ReleasePurchaseAuthorizationInput(idempotencyKey, creditAccountId, authId);
        var output = releasePurchaseAuthorizationUseCase.execute(input);
        return ResponseEntity.ok(CreditAccountDtoMapper.toResponse(output.account()));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<CreditAccountResponse> receivePayment(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReceivePaymentRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var input = new ReceivePaymentInput(idempotencyKey, creditAccountId, Money.positive(request.amount()));
        var output = receivePaymentUseCase.execute(input);
        return ResponseEntity.ok(CreditAccountDtoMapper.toResponse(output.account()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreditAccountResponse> getAccount(
            @PathVariable String id,
            @RequestParam(value = "minVersion", required = false) Long minVersion) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var input = new GetCreditAccountInput(creditAccountId, minVersion);
        var output = getCreditAccountUseCase.execute(input);
        return ResponseEntity.ok(CreditAccountDtoMapper.toResponse(output.account()));
    }

    @GetMapping
    public ResponseEntity<PageResponse> listAccounts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        var input = new ListCreditAccountsInput(page, size);
        var output = listCreditAccountsUseCase.execute(input);
        var items = output.items().stream()
                .map(CreditAccountDtoMapper::toResponse)
                .toList();
        return ResponseEntity.ok(new PageResponse(
                items,
                output.page(),
                output.size(),
                output.totalItems(),
                output.totalPages()
        ));
    }
}
```

- [ ] **Step 3: Compile to verify**.

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**.

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java
git commit -m "refactor: use explicit DTOs in CreditAccountController"
```

---

### Task 4: Refactor `RestExceptionHandler`

**Files:**

- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestExceptionHandler.java`

**Interfaces:**

- Consumes: `ErrorResponse`.
- Produces: Handlers returning `ResponseEntity<ErrorResponse>`.

- [ ] **Step 1: Update imports and signatures.**

Add import:

```java
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.ErrorResponse;
```

Remove import:

```java
import java.util.Map;
```

- [ ] **Step 2: Replace `Map<String, String>` with `ErrorResponse` in all handlers (except `ProjectionNotReadyException`).**

Each handler body becomes:

```java
return ResponseEntity.status(HttpStatus.<STATUS>)
    .body(new ErrorResponse(ex.getMessage()));
```

Full file content:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.ErrorResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.ProjectionNotReadyResponse;
import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidPageSizeException;
import com.sanmoo.eventsourcing.creditaccount.core.error.ProjectionNotReadyException;
import com.sanmoo.eventsourcing.creditaccount.core.error.SummaryNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountAlreadyExistsException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

  @ExceptionHandler(AccountAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleAccountAlreadyExists(AccountAlreadyExistsException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(ConcurrencyConflictException.class)
  public ResponseEntity<ErrorResponse> handleConcurrencyConflict(ConcurrencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(AccountNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(SummaryNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleSummaryNotFound(SummaryNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(ProjectionNotReadyException.class)
  public ResponseEntity<ProjectionNotReadyResponse> handleProjectionNotReady(ProjectionNotReadyException ex) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(new ProjectionNotReadyResponse(
                ex.getMessage(),
                ex.getCreditAccountId(),
                ex.getCurrentProjectionVersion(),
                ex.getRequiredVersion()
        ));
  }

  @ExceptionHandler(InvalidPageSizeException.class)
  public ResponseEntity<ErrorResponse> handleInvalidPageSize(InvalidPageSizeException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
        .body(new ErrorResponse(ex.getMessage()));
  }
}
```

- [ ] **Step 3: Compile to verify**.

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**.

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestExceptionHandler.java
git commit -m "refactor: use ErrorResponse in RestExceptionHandler"
```

---

### Task 5: Update `CreditAccountControllerIT`

**Files:**

- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerIT.java`

**Interfaces:**

- Consumes: `CreditAccountResponse`, `AuthorizePurchaseResponse`.
- Produces: Tests asserting typed fields.

- [ ] **Step 1: Replace `Map.class` with typed DTOs and field access.**

Full file content:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.AuthorizePurchaseResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.CreditAccountResponse;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CreditAccountControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectionWorker projectionWorker;

    private RestTemplate restTemplate;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        baseUrl = "http://localhost:" + port + "/credit-accounts";
    }

    @Test
    void fullHappyPath() {
        var openResponse = restTemplate.postForEntity(
                baseUrl,
                createRequest(null),
                CreditAccountResponse.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accountId = openResponse.getBody().creditAccountId();

        var limitResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/credit-limit",
                new HttpEntity<>(Map.of("limit", "500.00"), createHeaders()),
                CreditAccountResponse.class
        );
        assertThat(limitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String authorizationId = UUID.randomUUID().toString();
        var authResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/purchases/authorizations",
                new HttpEntity<>(Map.of(
                        "authorizationId", authorizationId,
                        "amount", "100.00",
                        "merchantName", "Store"
                ), createHeaders()),
                AuthorizePurchaseResponse.class
        );
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authResponse.getBody().authorizationId()).isEqualTo(authorizationId);

        projectionWorker.processOnce(50);

        var getAfterAuth = restTemplate.getForEntity(
                baseUrl + "/" + accountId,
                CreditAccountResponse.class
        );
        assertThat(getAfterAuth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterAuth.getBody().availableLimit()).isEqualTo("400.00");

        var captureResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/purchases/authorizations/" + authorizationId + "/capture",
                createRequest(null),
                CreditAccountResponse.class
        );
        assertThat(captureResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        projectionWorker.processOnce(50);

        var getAfterCapture = restTemplate.getForEntity(
                baseUrl + "/" + accountId,
                CreditAccountResponse.class
        );
        assertThat(getAfterCapture.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterCapture.getBody().outstandingBalance()).isEqualTo("100.00");

        var paymentResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/payments",
                new HttpEntity<>(Map.of("amount", "50.00"), createHeaders()),
                CreditAccountResponse.class
        );
        assertThat(paymentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        projectionWorker.processOnce(50);

        var getAfterPayment = restTemplate.getForEntity(
                baseUrl + "/" + accountId,
                CreditAccountResponse.class
        );
        assertThat(getAfterPayment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterPayment.getBody().outstandingBalance()).isEqualTo("50.00");
    }

    @Test
    void authorizesAndReleasesUsingReturnedAuthorizationId() {
        var openResponse = restTemplate.postForEntity(baseUrl, createRequest(null), CreditAccountResponse.class);
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accountId = openResponse.getBody().creditAccountId();

        var limitResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/credit-limit",
                new HttpEntity<>(Map.of("limit", "500.00"), createHeaders()),
                CreditAccountResponse.class
        );
        assertThat(limitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String authorizationId = UUID.randomUUID().toString();
        var authResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/purchases/authorizations",
                new HttpEntity<>(Map.of(
                        "authorizationId", authorizationId,
                        "amount", "100.00",
                        "merchantName", "Store"
                ), createHeaders()),
                AuthorizePurchaseResponse.class
        );
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authResponse.getBody().authorizationId()).isEqualTo(authorizationId);

        var releaseResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/purchases/authorizations/" + authorizationId + "/release",
                createRequest(null),
                CreditAccountResponse.class
        );
        assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        projectionWorker.processOnce(50);

        var getAfterRelease = restTemplate.getForEntity(baseUrl + "/" + accountId, CreditAccountResponse.class);
        assertThat(getAfterRelease.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterRelease.getBody().authorizedAmount()).isEqualTo("0.00");
        assertThat(getAfterRelease.getBody().availableLimit()).isEqualTo("500.00");
    }

    @Test
    void idempotencyRecordsDoNotHaveDurableStartedStatusColumn() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_name = 'idempotency_records'
                  AND column_name = 'status'
                """,
                Integer.class
        );

        assertThat(count).isZero();
    }

    @Test
    void idempotencyKeyReturnsSameResult() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set("Idempotency-Key", "fixed-key-123");
        var request = new HttpEntity<>(Map.of("limit", "500.00"), headers);

        var response1 = restTemplate.postForEntity(
                baseUrl,
                request,
                CreditAccountResponse.class
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var response2 = restTemplate.postForEntity(
                baseUrl,
                request,
                CreditAccountResponse.class
        );
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody()).isEqualTo(response1.getBody());
    }

    private HttpEntity<?> createRequest(Object body) {
        return new HttpEntity<>(body, createHeaders());
    }

    private HttpHeaders createHeaders() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return headers;
    }
}
```

- [ ] **Step 2: Run the integration test class**.

Run: `./mvnw test -Dtest=CreditAccountControllerIT -q`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 3: Commit**.

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerIT.java
git commit -m "test: use typed response DTOs in CreditAccountControllerIT"
```

---

### Task 6: Update `CreditAccountControllerListIT`

**Files:**

- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerListIT.java`

**Interfaces:**

- Consumes: `CreditAccountResponse`, `PageResponse`, `ProjectionNotReadyResponse`.
- Produces: Tests asserting typed fields.

- [ ] **Step 1: Replace `Map.class` with typed DTOs and field access.**

Full file content:

```java
package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.CreditAccountApplication;
import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.CreditAccountResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.PageResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.ProjectionNotReadyResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapter;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {CreditAccountApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CreditAccountControllerListIT {

    @LocalServerPort
    private int port;

    @Autowired
    JdbcEventStoreAdapter eventStoreAdapter;

    @Autowired
    ProjectionWorker projectionWorker;

    private RestTemplate rest;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void getById_returns404_whenSummaryMissing() {
        UUID id = UUID.randomUUID();
        try {
            rest.getForEntity(url("/credit-accounts/" + id), CreditAccountResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void getById_returns200_withProjectedVersion() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())), Map.of());
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 1,
                List.of(new CreditLimitAssigned(CreditAccountId.of(id), Money.of("1000.00"), Instant.now())), Map.of());
        projectionWorker.processOnce(50);

        ResponseEntity<CreditAccountResponse> response = rest.getForEntity(url("/credit-accounts/" + id), CreditAccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().creditAccountId()).isEqualTo(id.toString());
        assertThat(response.getBody().projectedVersion()).isEqualTo(2L);
    }

    @Test
    void getById_returns202_whenMinVersionNotReached() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())), Map.of());

        ResponseEntity<ProjectionNotReadyResponse> response = rest.getForEntity(
                url("/credit-accounts/" + id + "?minVersion=5"),
                ProjectionNotReadyResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().requiredVersion()).isEqualTo(5L);
    }

    @Test
    void list_returns400_whenSizeAboveMax() {
        try {
            rest.getForEntity(url("/credit-accounts?size=200"), PageResponse.class);
        } catch (HttpClientErrorException.BadRequest e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void list_returnsPagedItems() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())), Map.of());
        projectionWorker.processOnce(50);

        ResponseEntity<PageResponse> response = rest.getForEntity(url("/credit-accounts"), PageResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<CreditAccountResponse> items = response.getBody().items();
        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(item -> id.toString().equals(item.creditAccountId()));
    }
}
```

- [ ] **Step 2: Run the integration test class**.

Run: `./mvnw test -Dtest=CreditAccountControllerListIT -q`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 3: Commit**.

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountControllerListIT.java
git commit -m "test: use typed response DTOs in CreditAccountControllerListIT"
```

---

### Task 7: Full Verification

**Files:**

- All modified files.

- [ ] **Step 1: Run the full test suite**.

Run: `./mvnw verify -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run LSP diagnostics on touched directories**.

Run: diagnostics on `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest` and `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest`.
Expected: No errors.

- [ ] **Step 3: Final review**.

Confirm:

- `CreditAccountController` no longer references `Map<String, Object>`.
- `PageResponse` uses `List<CreditAccountResponse>`.
- `RestExceptionHandler` no longer references `Map<String, String>`.
- No `toMap` method remains in the controller.
- All integration tests pass.

- [ ] **Step 4: Commit any remaining changes**.

If verification produced no new changes, no commit needed. Otherwise:

```bash
git add -A
git commit -m "chore: final verification adjustments"
```

---

## Spec Coverage Checklist

| Spec Requirement | Task |
|---|---|
| Create `CreditAccountResponse` | Task 1 |
| Create `AuthorizationResponse` | Task 1 |
| Create `AuthorizePurchaseResponse` | Task 1 |
| Create `ErrorResponse` | Task 1 |
| Create `CreditAccountDtoMapper` | Task 1 |
| Update `PageResponse` to typed list | Task 2 |
| Refactor `CreditAccountController` | Task 3 |
| Refactor `RestExceptionHandler` | Task 4 |
| Update `CreditAccountControllerIT` | Task 5 |
| Update `CreditAccountControllerListIT` | Task 6 |
| Full build/test verification | Task 7 |
