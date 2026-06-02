package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.*;
import com.sanmoo.eventsourcing.creditaccount.application.command.*;
import com.sanmoo.eventsourcing.creditaccount.application.service.CreditAccountCommandService;
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

    private final CreditAccountCommandService commandService;

    public CreditAccountController(CreditAccountCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> openCreditAccount(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) OpenCreditAccountRequest request) {
        var command = new OpenCreditAccountCommand(idempotencyKey);
        var result = commandService.openCreditAccount(command);
        if (result.replayed()) {
            return ResponseEntity.ok(result.responseData());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.responseData());
    }

    @PostMapping("/{id}/credit-limit")
    public ResponseEntity<Map<String, Object>> assignCreditLimit(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AssignCreditLimitRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var command = new AssignCreditLimitCommand(
                idempotencyKey, creditAccountId, Money.positive(request.limit()));
        var result = commandService.assignCreditLimit(command);
        return ResponseEntity.ok(result.responseData());
    }

    @PostMapping("/{id}/purchases/authorizations")
    public ResponseEntity<Map<String, Object>> authorizePurchase(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizePurchaseRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authorizationId = AuthorizationId.of(UUID.fromString(request.authorizationId()));
        var command = new AuthorizePurchaseCommand(
                idempotencyKey, creditAccountId, authorizationId,
                Money.positive(request.amount()), request.merchantName());
        var result = commandService.authorizePurchase(command);
        Map<String, Object> response = new LinkedHashMap<>(result.responseData());
        response.put("authorizationId", request.authorizationId());
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
        var command = new CapturePurchaseCommand(idempotencyKey, creditAccountId, authId);
        var result = commandService.capturePurchase(command);
        return ResponseEntity.ok(result.responseData());
    }

    @PostMapping("/{id}/purchases/authorizations/{authorizationId}/release")
    public ResponseEntity<Map<String, Object>> releasePurchaseAuthorization(
            @PathVariable String id,
            @PathVariable String authorizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) ReleasePurchaseAuthorizationRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var authId = AuthorizationId.of(UUID.fromString(authorizationId));
        var command = new ReleasePurchaseAuthorizationCommand(idempotencyKey, creditAccountId, authId);
        var result = commandService.releasePurchaseAuthorization(command);
        return ResponseEntity.ok(result.responseData());
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<Map<String, Object>> receivePayment(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReceivePaymentRequest request) {
        var creditAccountId = CreditAccountId.of(UUID.fromString(id));
        var command = new ReceivePaymentCommand(
                idempotencyKey, creditAccountId, Money.positive(request.amount()));
        var result = commandService.receivePayment(command);
        return ResponseEntity.ok(result.responseData());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAccount(@PathVariable String id) {
        var response = commandService.getAccount(id);
        return ResponseEntity.ok(response);
    }
}
