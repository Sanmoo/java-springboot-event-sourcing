package com.sanmoo.eventsourcing.creditaccount.application.service;

import com.sanmoo.eventsourcing.creditaccount.application.command.*;
import com.sanmoo.eventsourcing.creditaccount.application.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.application.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.application.result.CommandResult;
import com.sanmoo.eventsourcing.creditaccount.domain.CreditAccount;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.model.*;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class CreditAccountCommandService {

    private static final String AGGREGATE_TYPE = "CreditAccount";

    private final EventStorePort eventStore;
    private final IdempotencyPort idempotencyPort;
    private final ObjectMapper objectMapper;

    public CreditAccountCommandService(EventStorePort eventStore, IdempotencyPort idempotencyPort, ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.idempotencyPort = idempotencyPort;
        this.objectMapper = objectMapper;
    }

    public CommandResult openCreditAccount(OpenCreditAccountCommand command) {
        CreditAccountId creditAccountId = CreditAccountId.newId();
        String aggregateId = creditAccountId.value().toString();
        String requestHash = calculateRequestHash(command);

        IdempotencyDecision decision = idempotencyPort.start(
                command.idempotencyKey(), "OpenCreditAccount", aggregateId, requestHash);

        return switch (decision) {
            case IdempotencyDecision.Replay replay -> handleReplay(replay);
            case IdempotencyDecision.Conflict conflict -> throw new IdempotencyConflictException(conflict.message());
            case IdempotencyDecision.Started started -> executeAndStore(
                    command.idempotencyKey(), aggregateId, creditAccountId, account -> account.open(now())
            );
        };
    }

    public CommandResult assignCreditLimit(AssignCreditLimitCommand command) {
        String aggregateId = command.creditAccountId().value().toString();
        String requestHash = calculateRequestHash(command);

        IdempotencyDecision decision = idempotencyPort.start(
                command.idempotencyKey(), "AssignCreditLimit", aggregateId, requestHash);

        return switch (decision) {
            case IdempotencyDecision.Replay replay -> handleReplay(replay);
            case IdempotencyDecision.Conflict conflict -> throw new IdempotencyConflictException(conflict.message());
            case IdempotencyDecision.Started started -> executeAndStore(
                    command.idempotencyKey(), aggregateId, command.creditAccountId(),
                    account -> account.assignCreditLimit(command.creditLimit(), now())
            );
        };
    }

    public CommandResult changeCreditLimit(ChangeCreditLimitCommand command) {
        String aggregateId = command.creditAccountId().value().toString();
        String requestHash = calculateRequestHash(command);

        IdempotencyDecision decision = idempotencyPort.start(
                command.idempotencyKey(), "ChangeCreditLimit", aggregateId, requestHash);

        return switch (decision) {
            case IdempotencyDecision.Replay replay -> handleReplay(replay);
            case IdempotencyDecision.Conflict conflict -> throw new IdempotencyConflictException(conflict.message());
            case IdempotencyDecision.Started started -> executeAndStore(
                    command.idempotencyKey(), aggregateId, command.creditAccountId(),
                    account -> account.changeCreditLimit(command.newCreditLimit(), now())
            );
        };
    }

    public CommandResult authorizePurchase(AuthorizePurchaseCommand command) {
        String aggregateId = command.creditAccountId().value().toString();
        String requestHash = calculateRequestHash(command);

        IdempotencyDecision decision = idempotencyPort.start(
                command.idempotencyKey(), "AuthorizePurchase", aggregateId, requestHash);

        return switch (decision) {
            case IdempotencyDecision.Replay replay -> handleReplay(replay);
            case IdempotencyDecision.Conflict conflict -> throw new IdempotencyConflictException(conflict.message());
            case IdempotencyDecision.Started started -> executeAndStore(
                    command.idempotencyKey(), aggregateId, command.creditAccountId(),
                    account -> account.authorizePurchase(command.authorizationId(), command.amount(), command.merchantName(), now())
            );
        };
    }

    public CommandResult capturePurchase(CapturePurchaseCommand command) {
        String aggregateId = command.creditAccountId().value().toString();
        String requestHash = calculateRequestHash(command);

        IdempotencyDecision decision = idempotencyPort.start(
                command.idempotencyKey(), "CapturePurchase", aggregateId, requestHash);

        return switch (decision) {
            case IdempotencyDecision.Replay replay -> handleReplay(replay);
            case IdempotencyDecision.Conflict conflict -> throw new IdempotencyConflictException(conflict.message());
            case IdempotencyDecision.Started started -> executeAndStore(
                    command.idempotencyKey(), aggregateId, command.creditAccountId(),
                    account -> account.capturePurchase(command.authorizationId(), now())
            );
        };
    }

    public CommandResult releasePurchaseAuthorization(ReleasePurchaseAuthorizationCommand command) {
        String aggregateId = command.creditAccountId().value().toString();
        String requestHash = calculateRequestHash(command);

        IdempotencyDecision decision = idempotencyPort.start(
                command.idempotencyKey(), "ReleasePurchaseAuthorization", aggregateId, requestHash);

        return switch (decision) {
            case IdempotencyDecision.Replay replay -> handleReplay(replay);
            case IdempotencyDecision.Conflict conflict -> throw new IdempotencyConflictException(conflict.message());
            case IdempotencyDecision.Started started -> executeAndStore(
                    command.idempotencyKey(), aggregateId, command.creditAccountId(),
                    account -> account.releasePurchaseAuthorization(command.authorizationId(), now())
            );
        };
    }

    public CommandResult receivePayment(ReceivePaymentCommand command) {
        String aggregateId = command.creditAccountId().value().toString();
        String requestHash = calculateRequestHash(command);

        IdempotencyDecision decision = idempotencyPort.start(
                command.idempotencyKey(), "ReceivePayment", aggregateId, requestHash);

        return switch (decision) {
            case IdempotencyDecision.Replay replay -> handleReplay(replay);
            case IdempotencyDecision.Conflict conflict -> throw new IdempotencyConflictException(conflict.message());
            case IdempotencyDecision.Started started -> executeAndStore(
                    command.idempotencyKey(), aggregateId, command.creditAccountId(),
                    account -> account.receivePayment(command.amount(), now())
            );
        };
    }

    @FunctionalInterface
    private interface CommandExecutor {
        List<CreditAccountEvent> execute(CreditAccount account);
    }

    private CommandResult executeAndStore(
            String idempotencyKey,
            String aggregateId,
            CreditAccountId creditAccountId,
            CommandExecutor executor
    ) {
        List<CreditAccountEvent> history = loadHistory(aggregateId);
        CreditAccount account = CreditAccount.rehydrate(creditAccountId, history);

        List<CreditAccountEvent> newEvents = executor.execute(account);

        // Save version before applying to use as expected version for event store
        long expectedVersion = account.version();

        // Apply new events before snapshotting for accurate response state
        account.applyAll(newEvents);

        AppendResult appendResult = eventStore.appendEvents(
                AGGREGATE_TYPE, aggregateId, expectedVersion, newEvents, Map.of());

        Map<String, Object> responseData = buildResponseData(account);
        CommandResult result = new CommandResult(aggregateId, appendResult.newAggregateVersion(), responseData);

        try {
            String payload = objectMapper.writeValueAsString(result);
            idempotencyPort.complete(idempotencyKey, payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response for idempotency", e);
        }

        return result;
    }

    private CommandResult handleReplay(IdempotencyDecision.Replay replay) {
        try {
            return objectMapper.readValue(replay.responsePayload(), CommandResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize idempotency response payload", e);
        }
    }

    private List<CreditAccountEvent> loadHistory(String aggregateId) {
        return eventStore.loadEvents(AGGREGATE_TYPE, aggregateId)
                .stream()
                .map(EventEnvelope::event)
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildResponseData(CreditAccount account) {
        CreditAccountSnapshot snapshot = account.snapshot();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("creditAccountId", snapshot.id().value().toString());
        data.put("opened", snapshot.opened());
        data.put("creditLimit", snapshot.creditLimit() != null ? snapshot.creditLimit().amount().toPlainString() : null);
        data.put("outstandingBalance", snapshot.outstandingBalance().amount().toPlainString());
        data.put("authorizedAmount", snapshot.authorizedAmount().amount().toPlainString());
        // availableLimit requires a credit limit; before the limit is assigned it defaults to zero
        data.put("availableLimit", snapshot.creditLimit() != null
                ? snapshot.availableLimit().amount().toPlainString()
                : Money.zero().amount().toPlainString());

        List<Map<String, Object>> authList = snapshot.authorizations().values().stream()
                .map(auth -> {
                    Map<String, Object> authMap = new LinkedHashMap<>();
                    authMap.put("authorizationId", auth.id().value().toString());
                    authMap.put("amount", auth.amount().amount().toPlainString());
                    authMap.put("status", auth.status().name());
                    authMap.put("merchantName", auth.merchantName());
                    return authMap;
                })
                .collect(Collectors.toList());
        data.put("authorizations", authList);

        return data;
    }

    private String calculateRequestHash(Object command) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(command);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(serialized);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request", e);
        }
    }

    private Instant now() {
        return Instant.now();
    }


}
