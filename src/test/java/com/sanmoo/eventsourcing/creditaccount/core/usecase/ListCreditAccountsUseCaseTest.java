package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidPageSizeException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ListCreditAccountsInput;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListCreditAccountsUseCaseTest {

    private final CreditAccountSummaryRepository repo = mock(CreditAccountSummaryRepository.class);
    private final ListCreditAccountsUseCase useCase = new ListCreditAccountsUseCase(repo);

    @Test
    void rejects_sizeAboveMax() {
        assertThatThrownBy(() -> useCase.execute(new ListCreditAccountsInput(0, 200)))
                .isInstanceOf(InvalidPageSizeException.class);
    }

    @Test
    void rejects_negativePage() {
        assertThatThrownBy(() -> useCase.execute(new ListCreditAccountsInput(-1, 20)))
                .isInstanceOf(InvalidPageSizeException.class);
    }

    @Test
    void delegatesToRepository_withDefaults() {
        when(repo.findAll(any())).thenReturn(new CreditAccountSummaryPage(List.of(), 0, 20, 0, 0));
        useCase.execute(new ListCreditAccountsInput(0, 20));
        ArgumentCaptor<CreditAccountSummaryPageRequest> captor = ArgumentCaptor.forClass(CreditAccountSummaryPageRequest.class);
        verify(repo).findAll(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(0);
        assertThat(captor.getValue().size()).isEqualTo(20);
    }
}
