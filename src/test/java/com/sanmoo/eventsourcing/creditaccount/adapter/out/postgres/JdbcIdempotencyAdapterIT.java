package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyDecision;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
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
    void firstKeyHashesToStarted() {
        // given
        var key = UUID.randomUUID().toString();
        var commandType = "CreateAccount";
        var aggregateId = UUID.randomUUID().toString();
        var requestHash = "hash-123";

        // when
        IdempotencyDecision decision = idempotencyPort.start(key, commandType, aggregateId, requestHash);

        // then
        assertThat(decision).isInstanceOf(IdempotencyDecision.Started.class);
        var started = (IdempotencyDecision.Started) decision;
        assertThat(started.key()).isEqualTo(key);
    }

    @Test
    void repeatedKeyWithSameHashReturnsReplay() {
        // given
        var key = UUID.randomUUID().toString();
        var commandType = "CreateAccount";
        var aggregateId = UUID.randomUUID().toString();
        var requestHash = "hash-456";
        var responsePayload = "{\"status\":\"ok\"}";

        // when - first start + complete
        IdempotencyDecision first = idempotencyPort.start(key, commandType, aggregateId, requestHash);
        assertThat(first).isInstanceOf(IdempotencyDecision.Started.class);
        idempotencyPort.complete(key, responsePayload);

        // then - repeated start with same key and hash returns Replay
        IdempotencyDecision second = idempotencyPort.start(key, commandType, aggregateId, requestHash);
        assertThat(second).isInstanceOf(IdempotencyDecision.Replay.class);
        var replay = (IdempotencyDecision.Replay) second;
        assertThat(replay.responsePayload()).isEqualTo(responsePayload);
    }

    @Test
    void repeatedKeyWithDifferentHashReturnsConflict() {
        // given
        var key = UUID.randomUUID().toString();
        var commandType = "CreateAccount";
        var aggregateId = UUID.randomUUID().toString();
        var requestHash1 = "hash-789";
        var requestHash2 = "hash-different";

        // when - start with first hash
        IdempotencyDecision first = idempotencyPort.start(key, commandType, aggregateId, requestHash1);
        assertThat(first).isInstanceOf(IdempotencyDecision.Started.class);

        // then - start again with same key but different hash returns Conflict
        IdempotencyDecision second = idempotencyPort.start(key, commandType, aggregateId, requestHash2);
        assertThat(second).isInstanceOf(IdempotencyDecision.Conflict.class);
    }
}
