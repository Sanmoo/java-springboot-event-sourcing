package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.ProjectionNotReadyException;
import com.sanmoo.eventsourcing.creditaccount.core.error.SummaryNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetCreditAccountUseCaseTest {

    private final CreditAccountSummaryRepository repo = mock(CreditAccountSummaryRepository.class);
    private final GetCreditAccountUseCase useCase = new GetCreditAccountUseCase(repo);

    @Test
    void returnsAccountWhenSummaryFound() {
        UUID id = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(id);
        var summary = new CreditAccountSummary(
                id, true, "1000.00", "500.00", "200.00", "300.00",
                List.of(), 5L, UUID.randomUUID(), Instant.now()
        );
        when(repo.findById(creditAccountId)).thenReturn(Optional.of(summary));

        var output = useCase.execute(new GetCreditAccountInput(creditAccountId, null));

        CreditAccountOutput account = output.account();
        assertThat(account.creditAccountId()).isEqualTo(id.toString());
        assertThat(account.opened()).isTrue();
        assertThat(account.creditLimit()).isEqualTo("1000.00");
        assertThat(account.outstandingBalance()).isEqualTo("500.00");
        assertThat(account.authorizedAmount()).isEqualTo("200.00");
        assertThat(account.availableLimit()).isEqualTo("300.00");
        assertThat(account.projectedVersion()).isEqualTo(5L);
    }

    @Test
    void throwsSummaryNotFoundWhenMissingWithoutMinVersion() {
        UUID id = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(id);
        when(repo.findById(creditAccountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountInput(creditAccountId, null)))
                .isInstanceOf(SummaryNotFoundException.class);
    }

    @Test
    void throwsProjectionNotReadyWhenMissingWithMinVersion() {
        UUID id = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(id);
        when(repo.findById(creditAccountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountInput(creditAccountId, 5L)))
                .isInstanceOf(ProjectionNotReadyException.class);
    }

    @Test
    void throwsProjectionNotReadyWhenBehindMinVersion() {
        UUID id = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(id);
        var summary = new CreditAccountSummary(
                id, true, "1000.00", "500.00", "200.00", "300.00",
                List.of(), 3L, UUID.randomUUID(), Instant.now()
        );
        when(repo.findById(creditAccountId)).thenReturn(Optional.of(summary));

        assertThatThrownBy(() -> useCase.execute(new GetCreditAccountInput(creditAccountId, 5L)))
                .isInstanceOf(ProjectionNotReadyException.class);
    }

    @Test
    void returnsAccountWhenMinVersionMet() {
        UUID id = UUID.randomUUID();
        CreditAccountId creditAccountId = CreditAccountId.of(id);
        var summary = new CreditAccountSummary(
                id, true, "1000.00", "500.00", "200.00", "300.00",
                List.of(), 5L, UUID.randomUUID(), Instant.now()
        );
        when(repo.findById(creditAccountId)).thenReturn(Optional.of(summary));

        var output = useCase.execute(new GetCreditAccountInput(creditAccountId, 5L));

        assertThat(output.account().projectedVersion()).isEqualTo(5L);
    }
}
