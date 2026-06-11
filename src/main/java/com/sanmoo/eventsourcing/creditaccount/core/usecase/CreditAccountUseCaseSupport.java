package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.PurchaseAuthorizationOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStore;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
import com.sanmoo.eventsourcing.creditaccount.domain.CreditAccount;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountEvent;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountSnapshot;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreditAccountUseCaseSupport {

    private static final String AGGREGATE_TYPE = "CreditAccount";

    private final EventStore eventStore;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
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

        idempotencyRepository.lockKey(idempotencyKey);

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!requestHash.equals(record.requestHash())) {
                throw new IdempotencyConflictException("idempotency key reused with different request hash");
            }
            ExecutionResult result = deserializeReplay(record.responsePayload());
            verifyReplayVersion(record, result);
            return outputMapper.apply(result);
        }

        ExecutionResult result = execute(aggregateId, creditAccountId, executor, idempotencyKey, commandType, requestHash);
        String payload = serializeResult(result);
        idempotencyRepository.saveResult(
                idempotencyKey,
                commandType,
                aggregateId,
                requestHash,
                payload,
                result.aggregateVersion()
        );
        return outputMapper.apply(result);
    }

    public CreditAccountOutput loadAccountOutput(CreditAccountId creditAccountId) {
        String aggregateId = creditAccountId.value().toString();
        List<CreditAccountEvent> history = loadHistory(aggregateId);
        CreditAccount account = CreditAccount.rehydrate(creditAccountId, history);
        if (!account.snapshot().opened()) {
            throw new com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotFoundException(
                    "Credit account not found: " + aggregateId);
        }
        return buildOutput(account, account.version());
    }

    private ExecutionResult execute(String aggregateId, CreditAccountId creditAccountId, CommandExecutor executor, String idempotencyKey, String commandType, String requestHash) {
        List<CreditAccountEvent> history = loadHistory(aggregateId);
        CreditAccount account = CreditAccount.rehydrate(creditAccountId, history);

        long expectedVersion = account.version();
        List<CreditAccountEvent> newEvents = executor.execute(account);

        Map<String, String> metadata = Map.of(
                "idempotencyKey", idempotencyKey,
                "commandType", commandType,
                "requestHash", requestHash
        );
        AppendResult appendResult = eventStore.appendEvents(
                AGGREGATE_TYPE, aggregateId, expectedVersion, newEvents, metadata);

        CreditAccountOutput output = buildOutput(account, appendResult.newAggregateVersion());
        return new ExecutionResult(output, appendResult.newAggregateVersion(), false);
    }

    private List<CreditAccountEvent> loadHistory(String aggregateId) {
        return eventStore.loadEvents(AGGREGATE_TYPE, aggregateId)
                .stream()
                .map(EventEnvelope::event)
                .collect(Collectors.toList());
    }

    private CreditAccountOutput buildOutput(CreditAccount account, Long projectedVersion) {
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
                authList,
                projectedVersion
        );
    }

    private ExecutionResult deserializeReplay(String responsePayload) {
        try {
            Map<String, Object> raw = objectMapper.readValue(responsePayload, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            long aggregateVersion = ((Number) raw.get("aggregateVersion")).longValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = (Map<String, Object>) raw.get("responseData");
            CreditAccountOutput output = objectMapper.convertValue(responseData, CreditAccountOutput.class);
            return new ExecutionResult(output, aggregateVersion, true);
        } catch (JacksonException | ClassCastException e) {
            throw new RuntimeException("Failed to deserialize idempotency response payload", e);
        }
    }

    private void verifyReplayVersion(IdempotencyRecord record, ExecutionResult result) {
        if (record.aggregateVersion() != result.aggregateVersion()) {
            throw new RuntimeException("Stored idempotency aggregate version does not match replay payload for key: "
                    + record.idempotencyKey());
        }
    }

    private String serializeResult(ExecutionResult result) {
        return serializeResponseResult(result, result.output());
    }

    private String serializeResponseResult(ExecutionResult result, Object response) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("aggregateId", result.output().creditAccountId());
            payload.put("aggregateVersion", result.aggregateVersion());
            payload.put("responseData", objectMapper.convertValue(response, Map.class));
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
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
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to hash request", e);
        }
    }

    @FunctionalInterface
    public interface CommandExecutor {
        List<CreditAccountEvent> execute(CreditAccount account);
    }

    public record ExecutionResult(CreditAccountOutput output, long aggregateVersion, boolean replayed) {}
}
