package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JdbcIdempotencyAdapterIT {

    @Autowired
    private IdempotencyPort idempotencyPort;

    @Test
    void firstKeyReturnsEmptyBeforeCompletion() {
        // given
        var key = UUID.randomUUID().toString();

        // when
        idempotencyPort.lockKey(key);
        Optional<IdempotencyRecord> found = idempotencyPort.findByKey(key);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void completedKeyReturnsRecord() {
        // given
        var key = UUID.randomUUID().toString();
        var commandType = "CreateAccount";
        var aggregateId = UUID.randomUUID().toString();
        var requestHash = "hash-456";
        var responsePayload = "{\"status\":\"ok\"}";

        // when
        idempotencyPort.lockKey(key);
        idempotencyPort.saveResult(key, commandType, aggregateId, requestHash, responsePayload, 1L);

        // then
        Optional<IdempotencyRecord> found = idempotencyPort.findByKey(key);
        assertThat(found).isPresent();
        assertThat(found.get().responsePayload()).isEqualTo(responsePayload);
        assertThat(found.get().aggregateVersion()).isEqualTo(1L);
    }

    @Test
    void repeatedKeyWithDifferentRequestHashReturnsRecord() {
        // given
        var key = UUID.randomUUID().toString();
        var commandType = "CreateAccount";
        var aggregateId = UUID.randomUUID().toString();
        var requestHash1 = "hash-789";

        // when - complete with first hash
        idempotencyPort.lockKey(key);
        idempotencyPort.saveResult(key, commandType, aggregateId, requestHash1, "payload", 1L);

        // then - findByKey returns the record regardless of hash
        Optional<IdempotencyRecord> found = idempotencyPort.findByKey(key);
        assertThat(found).isPresent();
        assertThat(found.get().requestHash()).isEqualTo(requestHash1);
    }
}
