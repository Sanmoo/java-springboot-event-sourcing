package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidEventHistoryPageException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AccountEvent;
import com.sanmoo.eventsourcing.creditaccount.core.port.AccountEventPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStore;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountEventsInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountEventsOutput;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetCreditAccountEventsUseCaseTest {

    private final EventStore eventStore = mock(EventStore.class);
    private final GetCreditAccountEventsUseCase useCase = new GetCreditAccountEventsUseCase(eventStore);

    @Test
    void getsTheRequestedHistoryPageFromEventStore() {
        UUID accountId = UUID.randomUUID();
        var expected = new AccountEvent(1, "credit-account.opened.v1", Instant.parse("2025-01-01T00:00:00Z"), Map.of());
        when(eventStore.readAccountEvents(accountId, 1, 1)).thenReturn(new AccountEventPage(List.of(expected), 2L, true));

        var output = useCase.execute(new GetCreditAccountEventsInput(CreditAccountId.of(accountId), 1, 1));

        assertThat(output.items()).containsExactly(new GetCreditAccountEventsOutput.Item(
                expected.version(), expected.type(), expected.occurredAt(), expected.payload()));
        assertThat(output.nextFromVersion()).isEqualTo(2L);
        verify(eventStore).readAccountEvents(accountId, 1, 1);
    }

    @Test
    void preservesNullPayloadFields() {
        UUID accountId = UUID.randomUUID();
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("optional", null);
        var expected = new AccountEvent(1, "credit-account.opened.v1", Instant.parse("2025-01-01T00:00:00Z"), payload);
        when(eventStore.readAccountEvents(accountId, 1, 1)).thenReturn(new AccountEventPage(List.of(expected), null, true));

        var output = useCase.execute(new GetCreditAccountEventsInput(CreditAccountId.of(accountId), 1, 1));

        assertThat(output.items().getFirst().payload()).containsEntry("optional", null);
    }

    @Test
    void rejectsInvalidPageValues() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountEventsInput(CreditAccountId.of(accountId), 0, 1)))
                .isInstanceOf(InvalidEventHistoryPageException.class);
        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountEventsInput(CreditAccountId.of(accountId), 1, 201)))
                .isInstanceOf(InvalidEventHistoryPageException.class);
    }

    @Test
    void rejectsUnknownAccount() {
        UUID accountId = UUID.randomUUID();
        when(eventStore.readAccountEvents(accountId, 1, 50)).thenReturn(new AccountEventPage(List.of(), null, false));

        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountEventsInput(CreditAccountId.of(accountId), 1, 50)))
                .hasMessageContaining("Credit account not found");
    }
}
