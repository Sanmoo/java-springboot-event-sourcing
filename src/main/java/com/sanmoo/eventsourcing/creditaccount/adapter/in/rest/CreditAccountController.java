package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

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
import java.util.LinkedHashMap;
import java.util.Map;
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
        Money limit = Money.positive(request.limit());
        try {
            var input = new AssignCreditLimitInput(idempotencyKey, creditAccountId, limit);
            var output = assignCreditLimitUseCase.execute(input);
            return ResponseEntity.ok(toMap(output.account()));
        } catch (CreditLimitAlreadyAssignedException e) {
            var changeInput = new ChangeCreditLimitInput(idempotencyKey, creditAccountId, limit);
            var changeOutput = changeCreditLimitUseCase.execute(changeInput);
            return ResponseEntity.ok(toMap(changeOutput.account()));
        }
    }

    @PostMapping("/{id}/purchases/authorizations")
    public ResponseEntity<Map<String, Object>> authorizePurchase(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizePurchaseRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authorizationId = AuthorizationId.of(request.authorizationId());
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
    public ResponseEntity<Map<String, Object>> getAccount(
            @PathVariable String id,
            @RequestParam(value = "minVersion", required = false) Long minVersion) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var input = new GetCreditAccountInput(creditAccountId, minVersion);
        var output = getCreditAccountUseCase.execute(input);
        return ResponseEntity.ok(toMap(output.account()));
    }

    @GetMapping
    public ResponseEntity<PageResponse> listAccounts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        var input = new ListCreditAccountsInput(page, size);
        var output = listCreditAccountsUseCase.execute(input);
        var items = output.items().stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(new PageResponse(
                items,
                output.page(),
                output.size(),
                output.totalItems(),
                output.totalPages()
        ));
    }

    private Map<String, Object> toMap(CreditAccountOutput output) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("creditAccountId", output.creditAccountId());
        data.put("opened", output.opened());
        data.put("creditLimit", output.creditLimit());
        data.put("outstandingBalance", output.outstandingBalance());
        data.put("authorizedAmount", output.authorizedAmount());
        data.put("availableLimit", output.availableLimit());
        data.put("projectedVersion", output.projectedVersion());
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
