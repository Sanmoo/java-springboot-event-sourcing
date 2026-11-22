package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidEventHistoryPageException;
import com.sanmoo.eventsourcing.creditaccount.core.port.AccountEventPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventStore;
import com.sanmoo.eventsourcing.creditaccount.core.port.EventTypeCatalog;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountEventsInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountEventsOutput;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class GetCreditAccountEventsUseCase {

    private static final int MAX_LIMIT = 200;
    private final EventStore eventStore;
    private final EventTypeCatalog eventTypeCatalog;

    public GetCreditAccountEventsUseCase(EventStore eventStore, EventTypeCatalog eventTypeCatalog) {
        this.eventStore = eventStore;
        this.eventTypeCatalog = eventTypeCatalog;
    }

    public GetCreditAccountEventsOutput execute(GetCreditAccountEventsInput input) {
        if (input.fromVersion() < 1) {
            throw new InvalidEventHistoryPageException("fromVersion must be >= 1");
        }
        if (input.limit() < 1 || input.limit() > MAX_LIMIT) {
            throw new InvalidEventHistoryPageException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (input.eventType() != null && !eventTypeCatalog.isKnown(input.eventType())) {
            throw new IllegalArgumentException("Unknown event type: " + input.eventType());
        }
        AccountEventPage page = eventStore.readAccountEvents(
                input.accountId().value(), input.fromVersion(), input.limit(), input.eventType());
        if (!page.accountExists()) {
            throw new AccountNotFoundException("Credit account not found: " + input.accountId().value());
        }
        var items = page.events().stream()
                .map(event -> new GetCreditAccountEventsOutput.Item(
                        event.version(), event.type(), event.occurredAt(), event.payload()))
                .toList();
        return new GetCreditAccountEventsOutput(items, page.nextFromVersion());
    }
}
