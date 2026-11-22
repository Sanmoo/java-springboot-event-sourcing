package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.core.port.AccountEventPage;
import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import org.springframework.context.annotation.Import;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventHistoryQueryTest {

    @Autowired
    private JdbcEventStoreAdapter eventStore;

    @Test
    void readAccountEventsReturnsEmptyForUnknownAccount() {
        AccountEventPage page = eventStore.readAccountEvents(UUID.randomUUID(), 1, 50, null);

        assertThat(page.events()).isEmpty();
        assertThat(page.nextFromVersion()).isNull();
        assertThat(page.accountExists()).isFalse();
    }
}
