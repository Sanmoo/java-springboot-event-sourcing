package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.domain.CreditAccount;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountSnapshot;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CreditAccountUseCaseSupport {

    private static final String AGGREGATE_TYPE = "CreditAccount";

    private final EventStorePort eventStore;
    private final IdempotencyPort idempotencyPort;
    private final ObjectMapper objectMapper;

    public CreditAccountUseCaseSupport(EventStorePort eventStore, IdempotencyPort idempotencyPort, ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.idempotencyPort = idempotencyPort;
        this.objectMapper = objectMapper;
    }

    public <I, O> O executeIdempotent(
            String idempotencyKey,
            String commandType,
            CreditAccountId creditAccountId,
            I input,
            CommandExecutor executor,
            Function<ExecutionResult, O> outputMapper
    ) {
        String aggregateId = creditAccountId.value().toString();
        String requestHash = calculateRequestHash(input);

        IdempotencyDecision decision = idempotencyPort.start(idempotencyKey, commandType, aggregateId, requestHash);

        return switch (decision) {
            case IdempotencyDecision.Replay replay -> {
                ExecutionResult result = deserializeReplay(replay);
                yield outputMapper.apply(result);
            }
            case IdempotencyDecision.Conflict conflict ->
                    throw new IdempotencyConflictException(conflict.message());
            case IdempotencyDecision.Started started -> {
                ExecutionResult result = execute(aggregateId, creditAccountId, executor);
                String payload = serializeResult(result);
                idempotencyPort.complete(idempotencyKey, payload);
                yield outputMapper.apply(result);
            }
        };
    }

    public CreditAccountOutput loadAccountOutput(CreditAccountId creditAccountId) {
        String aggregateId = creditAccountId.value().toString();
        List<CreditAccountEvent> history = loadHistory(aggregateId);
        CreditAccount account = CreditAccount.rehydrate(creditAccountId, history);
        if (!account.snapshot().opened()) {
            throw new com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotFoundException(
                    "Credit account not found: " + aggregateId);
        }
        return buildOutput(account);
    }

    private ExecutionResult execute(String aggregateId, CreditAccountId creditAccountId, CommandExecutor executor) {
        List<CreditAccountEvent> history = loadHistory(aggregateId);
        CreditAccount account = CreditAccount.rehydrate(creditAccountId, history);

        List<CreditAccountEvent> newEvents = executor.execute(account);
        long expectedVersion = account.version();
        account.applyAll(newEvents);

        AppendResult appendResult = eventStore.appendEvents(
                AGGREGATE_TYPE, aggregateId, expectedVersion, newEvents, Map.of());

        CreditAccountOutput output = buildOutput(account);
        return new ExecutionResult(output, appendResult.newAggregateVersion(), false);
    }

    private List<CreditAccountEvent> loadHistory(String aggregateId) {
        return eventStore.loadEvents(AGGREGATE_TYPE, aggregateId)
                .stream()
                .map(EventEnvelope::event)
                .collect(Collectors.toList());
    }

    private CreditAccountOutput buildOutput(CreditAccount account) {
        CreditAccountSnapshot snapshot = account.snapshot();

        List<PurchaseAuthorizationOutput> authList = snapshot.authorizations().values().stream()
                .map(auth -> new PurchaseAuthorizationOutput(
                        auth.id().value().toString(),
                        auth.amount().amount().toPlainString(),
                        auth.status().name(),
                        auth.merchantName()
                ))
                .collect(Collectors.toList());

        return new CreditAccountOutput(
                snapshot.id().value().toString(),
                snapshot.opened(),
                snapshot.creditLimit() != null ? snapshot.creditLimit().amount().toPlainString() : null,
                snapshot.outstandingBalance().amount().toPlainString(),
                snapshot.authorizedAmount().amount().toPlainString(),
                snapshot.creditLimit() != null
                        ? snapshot.availableLimit().amount().toPlainString()
                        : Money.zero().amount().toPlainString(),
                authList
        );
    }

    private ExecutionResult deserializeReplay(IdempotencyDecision.Replay replay) {
        try {
            Map<String, Object> raw = objectMapper.readValue(replay.responsePayload(), objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            String aggregateId = (String) raw.get("aggregateId");
            long aggregateVersion = ((Number) raw.get("aggregateVersion")).longValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = (Map<String, Object>) raw.get("responseData");
            CreditAccountOutput output = objectMapper.convertValue(responseData, CreditAccountOutput.class);
            return new ExecutionResult(output, aggregateVersion, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize idempotency response payload", e);
        }
    }

    private String serializeResult(ExecutionResult result) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("aggregateId", result.output().creditAccountId());
            payload.put("aggregateVersion", result.aggregateVersion());
            payload.put("responseData", objectMapper.convertValue(result.output(), Map.class));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response for idempotency", e);
        }
    }

    private String calculateRequestHash(Object input) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(input);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(serialized);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request", e);
        }
    }

    @FunctionalInterface
    public interface CommandExecutor {
        List<CreditAccountEvent> execute(CreditAccount account);
    }

    public record ExecutionResult(CreditAccountOutput output, long aggregateVersion, boolean replayed) {}
}
