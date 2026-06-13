package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.OutboxDeliveryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.ProjectionConfig;
import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StaleDeliveryRecoveryTest {

    @Test
    void recover_invokesRepositoryInsideTransaction() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var txRunner = mock(TransactionRunner.class);
        var config = mock(ProjectionConfig.class);
        
        when(config.getProcessingTimeout()).thenReturn(Duration.ofMinutes(2));
        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Integer> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(deliveries.recoverStaleProcessing(eq(Duration.ofMinutes(2)), eq(100))).thenReturn(7);

        var recovery = new StaleDeliveryRecovery(deliveries, config, txRunner);
        int recovered = recovery.recover();

        assertThat(recovered).isEqualTo(7);
        verify(deliveries).recoverStaleProcessing(Duration.ofMinutes(2), 100);
    }

    @Test
    void recover_returnsZeroWhenNothingStale() {
        var deliveries = mock(OutboxDeliveryRepository.class);
        var txRunner = mock(TransactionRunner.class);
        var config = mock(ProjectionConfig.class);

        when(txRunner.runInTransaction(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Integer> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(deliveries.recoverStaleProcessing(any(), anyInt())).thenReturn(0);

        var recovery = new StaleDeliveryRecovery(deliveries, config, txRunner);
        assertThat(recovery.recover()).isZero();
    }
}
