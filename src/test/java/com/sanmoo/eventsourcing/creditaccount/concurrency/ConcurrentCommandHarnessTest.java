package com.sanmoo.eventsourcing.creditaccount.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

class ConcurrentCommandHarnessTest {

    @Test
    void runConcurrently_executes_all_callables() throws Exception {
        List<Callable<String>> commands = List.of(() -> "a", () -> "b");
        try (var harness = new ConcurrentCommandHarness(2)) {
            ConcurrentCommandResult<String> result = harness.runConcurrently(commands, 3);

            assertThat(result.successfulResults()).containsExactlyInAnyOrder("a", "b");
            assertThat(result.conflictCount()).isZero();
            assertThat(result.failureCount()).isZero();
            assertThat(result.retryCount()).isZero();
        }
    }
}
