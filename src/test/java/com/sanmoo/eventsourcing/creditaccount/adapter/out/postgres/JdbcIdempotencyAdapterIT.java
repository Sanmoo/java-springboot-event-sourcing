package com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JdbcIdempotencyAdapterIT {

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM idempotency_records");
    }

    @Test
    void missingKeyReturnsEmptyResult() {
        Optional<IdempotencyRecord> result = idempotencyRepository.findByKey(UUID.randomUUID().toString());

        assertThat(result).isEmpty();
    }

    @Test
    void saveResultPersistsCompletedReplayRecord() {
        var key = UUID.randomUUID().toString();
        var commandType = "CreateAccount";
        var aggregateId = UUID.randomUUID().toString();
        var requestHash = "hash-456";
        var responsePayload = "{\"status\":\"ok\"}";

        idempotencyRepository.saveResult(key, commandType, aggregateId, requestHash, responsePayload);

        Optional<IdempotencyRecord> loaded = idempotencyRepository.findByKey(key);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().idempotencyKey()).isEqualTo(key);
        assertThat(loaded.get().commandType()).isEqualTo(commandType);
        assertThat(loaded.get().aggregateId()).isEqualTo(aggregateId);
        assertThat(loaded.get().requestHash()).isEqualTo(requestHash);
        assertThat(loaded.get().responsePayload()).isEqualTo(responsePayload);
    }

    @Test
    void findByKeyExposesStoredHashForCoreConflictDecision() {
        var key = UUID.randomUUID().toString();
        idempotencyRepository.saveResult(
                key,
                "CreateAccount",
                UUID.randomUUID().toString(),
                "original-hash",
                "{\"status\":\"ok\"}"
        );

        Optional<IdempotencyRecord> loaded = idempotencyRepository.findByKey(key);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().requestHash()).isEqualTo("original-hash");
    }

    @Test
    void lockKeySerializesConcurrentTransactionsForSameKey() throws Exception {
        var key = UUID.randomUUID().toString();
        var firstHasLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondAcquiredLock = new AtomicBoolean(false);
        var transactionTemplate = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyRepository.lockKey(key);
                firstHasLock.countDown();
                try {
                    assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }));

            assertThat(firstHasLock.await(10, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyRepository.lockKey(key);
                secondAcquiredLock.set(true);
            }));

            Thread.sleep(250);
            assertThat(secondAcquiredLock).isFalse();

            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(secondAcquiredLock).isTrue();
        }
    }

    @Test
    void lockKeyAllowsDifferentKeysToProceedConcurrently() throws Exception {
        var firstHasLock = new CountDownLatch(1);
        var secondAcquiredLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var transactionTemplate = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyRepository.lockKey("key-a-" + UUID.randomUUID());
                firstHasLock.countDown();
                try {
                    assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }));

            assertThat(firstHasLock.await(10, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                idempotencyRepository.lockKey("key-b-" + UUID.randomUUID());
                secondAcquiredLock.countDown();
            }));

            assertThat(secondAcquiredLock.await(2, TimeUnit.SECONDS)).isTrue();
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
    }
}
