package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.*;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.*;
import com.sanmoo.eventsourcing.creditaccount.domain.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/credit-accounts")
public class CreditAccountController {

    private final OpenCreditAccountUseCase openCreditAccountUseCase;
    private final AssignCreditLimitUseCase assignCreditLimitUseCase;
    private final AuthorizePurchaseUseCase authorizePurchaseUseCase;
    private final CapturePurchaseUseCase capturePurchaseUseCase;
    private final ReleasePurchaseAuthorizationUseCase releasePurchaseAuthorizationUseCase;
    private final ReceivePaymentUseCase receivePaymentUseCase;
    private final GetCreditAccountUseCase getCreditAccountUseCase;

    public CreditAccountController(
            OpenCreditAccountUseCase openCreditAccountUseCase,
            AssignCreditLimitUseCase assignCreditLimitUseCase,
            AuthorizePurchaseUseCase authorizePurchaseUseCase,
            CapturePurchaseUseCase capturePurchaseUseCase,
            ReleasePurchaseAuthorizationUseCase releasePurchaseAuthorizationUseCase,
            ReceivePaymentUseCase receivePaymentUseCase,
            GetCreditAccountUseCase getCreditAccountUseCase
    ) {
        this.openCreditAccountUseCase = openCreditAccountUseCase;
        this.assignCreditLimitUseCase = assignCreditLimitUseCase;
        this.authorizePurchaseUseCase = authorizePurchaseUseCase;
        this.capturePurchaseUseCase = capturePurchaseUseCase;
        this.releasePurchaseAuthorizationUseCase = releasePurchaseAuthorizationUseCase;
        this.receivePaymentUseCase = receivePaymentUseCase;
        this.getCreditAccountUseCase = getCreditAccountUseCase;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> openCreditAccount(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) OpenCreditAccountRequest request) {
        var input = new OpenCreditAccountInput(idempotencyKey);
        var output = openCreditAccountUseCase.execute(input);
        if (output.replayed()) {
            return ResponseEntity.ok(toMap(output.account()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(output.account()));
    }

    @PostMapping("/{id}/credit-limit")
    public ResponseEntity<Map<String, Object>> assignCreditLimit(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AssignCreditLimitRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var input = new AssignCreditLimitInput(idempotencyKey, creditAccountId, Money.positive(request.limit()));
        var output = assignCreditLimitUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    @PostMapping("/{id}/purchases/authorizations")
    public ResponseEntity<Map<String, Object>> authorizePurchase(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizePurchaseRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authorizationId = AuthorizationId.of(UUID.fromString(request.authorizationId()));
        var input = new AuthorizePurchaseInput(
                idempotencyKey, creditAccountId, authorizationId,
                Money.positive(request.amount()), request.merchantName());
        var output = authorizePurchaseUseCase.execute(input);
        Map<String, Object> response = new LinkedHashMap<>(toMap(output.account()));
        response.put("authorizationId", output.authorizationId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/purchases/authorizations/{authorizationId}/capture")
    public ResponseEntity<Map<String, Object>> capturePurchase(
            @PathVariable String id,
            @PathVariable String authorizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) CapturePurchaseRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authId = AuthorizationId.of(UUID.fromString(authorizationId));
        var input = new CapturePurchaseInput(idempotencyKey, creditAccountId, authId);
        var output = capturePurchaseUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    @PostMapping("/{id}/purchases/authorizations/{authorizationId}/release")
    public ResponseEntity<Map<String, Object>> releasePurchaseAuthorization(
            @PathVariable String id,
            @PathVariable String authorizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) ReleasePurchaseAuthorizationRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authId = AuthorizationId.of(UUID.fromString(authorizationId));
        var input = new ReleasePurchaseAuthorizationInput(idempotencyKey, creditAccountId, authId);
        var output = releasePurchaseAuthorizationUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<Map<String, Object>> receivePayment(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReceivePaymentRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var input = new ReceivePaymentInput(idempotencyKey, creditAccountId, Money.positive(request.amount()));
        var output = receivePaymentUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAccount(@PathVariable String id) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var input = new GetCreditAccountInput(creditAccountId);
        var output = getCreditAccountUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    private Map<String, Object> toMap(CreditAccountOutput output) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("creditAccountId", output.creditAccountId());
        data.put("opened", output.opened());
        data.put("creditLimit", output.creditLimit());
        data.put("outstandingBalance", output.outstandingBalance());
        data.put("authorizedAmount", output.authorizedAmount());
        data.put("availableLimit", output.availableLimit());
        data.put("authorizations", output.authorizations().stream()
                .map(auth -> {
                    Map<String, Object> authMap = new LinkedHashMap<>();
                    authMap.put("authorizationId", auth.authorizationId());
                    authMap.put("amount", auth.amount());
                    authMap.put("status", auth.status());
                    authMap.put("merchantName", auth.merchantName());
                    return authMap;
                })
                .toList());
        return data;
    }
}
