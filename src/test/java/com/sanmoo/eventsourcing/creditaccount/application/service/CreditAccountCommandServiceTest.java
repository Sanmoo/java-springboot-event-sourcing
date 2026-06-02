package com.sanmoo.eventsourcing.creditaccount.application.service;

import tools.jackson.databind.ObjectMapper;
import com.sanmoo.eventsourcing.creditaccount.application.command.OpenCreditAccountCommand;
import com.sanmoo.eventsourcing.creditaccount.application.port.AppendResult;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventEnvelope;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.application.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.application.result.CommandResult;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreditAccountCommandServiceTest {

    private EventStorePort eventStore;
    private IdempotencyPort idempotencyPort;
    private CreditAccountCommandService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStorePort.class);
        idempotencyPort = mock(IdempotencyPort.class);
        objectMapper = new ObjectMapper();
        service = new CreditAccountCommandService(eventStore, idempotencyPort, objectMapper);
    }

    @Test
    void openCreditAccountAppendsCreditAccountOpenedAtExpectedVersionZero() throws Exception {
        // Arrange
        OpenCreditAccountCommand command = new OpenCreditAccountCommand("test-key-1");
        when(idempotencyPort.start(eq("test-key-1"), eq("OpenCreditAccount"), any(), any()))
                .thenReturn(new IdempotencyDecision.Started("test-key-1"));
        when(eventStore.loadEvents(any(), any())).thenReturn(List.of());
        when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
                .thenReturn(new AppendResult(1L));

        // Act
        CommandResult result = service.openCreditAccount(command);

        // Assert
        verify(eventStore).appendEvents(
                eq("CreditAccount"),
                anyString(),
                eq(0L),
                argThat(events -> {
                    if (events.size() != 1) return false;
                    return events.get(0) instanceof CreditAccountOpened;
                }),
                anyMap()
        );
        verify(idempotencyPort).complete(eq("test-key-1"), anyString());

        assertThat(result.aggregateId()).isNotNull();
        assertThat(result.aggregateVersion()).isEqualTo(1L);
        assertThat(result.responseData()).isNotNull();
    }

    @Test
    void sameIdempotencyKeyAndHashReturnsPreviousResponseWithoutAppending() throws Exception {
        // Arrange
        OpenCreditAccountCommand command = new OpenCreditAccountCommand("test-key-2");

        // Simulate a stored response from a previous successful execution
        Map<String, Object> previousResponseData = Map.of(
                "creditAccountId", "550e8400-e29b-41d4-a716-446655440000",
                "opened", true
        );
        CommandResult previousResult = new CommandResult(
                "550e8400-e29b-41d4-a716-446655440000",
                1L,
                previousResponseData
        );
        String storedPayload = objectMapper.writeValueAsString(previousResult);

        when(idempotencyPort.start(eq("test-key-2"), eq("OpenCreditAccount"), any(), any()))
                .thenReturn(new IdempotencyDecision.Replay(storedPayload));

        // Act
        CommandResult result = service.openCreditAccount(command);

        // Assert
        verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
        verify(idempotencyPort, never()).complete(anyString(), anyString());

        assertThat(result.aggregateId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(result.aggregateVersion()).isEqualTo(1L);
        assertThat(result.responseData())
                .containsEntry("creditAccountId", "550e8400-e29b-41d4-a716-446655440000")
                .containsEntry("opened", true);
    }
}
