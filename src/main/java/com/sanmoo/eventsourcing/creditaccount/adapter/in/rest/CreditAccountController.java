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
