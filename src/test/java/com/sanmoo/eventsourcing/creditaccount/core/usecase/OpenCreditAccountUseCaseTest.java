package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenCreditAccountUseCaseTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private ObjectMapper objectMapper;
    private CreditAccountUseCaseSupport support;
    private UniqueIdGenerator uniqueIdGenerator;
    private OpenCreditAccountUseCase useCase;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        support = new CreditAccountUseCaseSupport(eventStore, idempotencyPort, objectMapper);
        uniqueIdGenerator = () -> UUID.fromString("018f5f4b-6a3c-7000-8000-000000000001");
        useCase = new OpenCreditAccountUseCase(support, uniqueIdGenerator);
    }

    @Test
    void executeAppendsCreditAccountOpenedAtExpectedVersionZero() {
        doNothing().when(idempotencyPort).lockKey(anyString());
        when(idempotencyPort.findByKey(anyString())).thenReturn(java.util.Optional.empty());
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of());
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(1L));

        var input = new OpenCreditAccountInput("key-1");
        var output = useCase.execute(input);

        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                eq("018f5f4b-6a3c-7000-8000-000000000001"),
                eq(0L),
                argThat(events -> events.size() == 1),
                anyMap()
        );
        assertThat(output.account().creditAccountId()).isEqualTo("018f5f4b-6a3c-7000-8000-000000000001");
        assertThat(output.replayed()).isFalse();
    }

    @Test
    void sameIdempotencyKeyAndHashReturnsReplayedOutput() throws Exception {
        var previousData = new java.util.LinkedHashMap<String, Object>();
        previousData.put("creditAccountId", "550e8400-e29b-41d4-a716-446655440000");
        previousData.put("opened", true);
        previousData.put("creditLimit", null);
        previousData.put("outstandingBalance", "0.00");
        previousData.put("authorizedAmount", "0.00");
        previousData.put("availableLimit", "0.00");
        previousData.put("authorizations", List.of());
        var previousPayload = objectMapper.writeValueAsString(Map.of(
                "aggregateId", "550e8400-e29b-41d4-a716-446655440000",
                "aggregateVersion", 1,
                "responseData", previousData
        ));

        doNothing().when(idempotencyPort).lockKey(anyString());
        when(idempotencyPort.findByKey(eq("key-2"))).thenReturn(java.util.Optional.of(
                new IdempotencyRecord(
                        "key-2",
                        "OpenCreditAccount",
                        "550e8400-e29b-41d4-a716-446655440000",
                        calculateRequestHash(new OpenCreditAccountInput("key-2")),
                        previousPayload,
                        1L
                )
        ));

        var input = new OpenCreditAccountInput("key-2");
        var output = useCase.execute(input);

        verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
        assertThat(output.account().creditAccountId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(output.replayed()).isTrue();
    }

    private String calculateRequestHash(Object input) throws Exception {
        byte[] serialized = objectMapper.writeValueAsBytes(input);
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(md.digest(serialized));
    }
}
