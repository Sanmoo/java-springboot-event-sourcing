package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.error.InvalidPageSizeException;
import com.sanmoo.eventsourcing.creditaccount.core.port.CreditAccountSummaryRepository;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPage;
import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummaryPageRequest;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ListCreditAccountsInput;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    @Test
    void mapsSummaryPageToOutput() {
        UUID accountId = UUID.randomUUID();
        var authorization = new CreditAccountSummary.AuthorizationSummary(
                UUID.randomUUID(), "25.00", "OPEN", "Store");
        var summary = new CreditAccountSummary(
                accountId,
                true,
                "500.00",
                "100.00",
                "25.00",
                "375.00",
                List.of(authorization),
                7L,
                UUID.randomUUID(),
                Instant.now());
        when(repo.findAll(any())).thenReturn(new CreditAccountSummaryPage(List.of(summary), 2, 10, 21, 3));

        var output = useCase.execute(new ListCreditAccountsInput(2, 10));

        assertThat(output.page()).isEqualTo(2);
        assertThat(output.size()).isEqualTo(10);
        assertThat(output.totalItems()).isEqualTo(21);
        assertThat(output.totalPages()).isEqualTo(3);
        assertThat(output.items()).hasSize(1);
        var item = output.items().get(0);
        assertThat(item.creditAccountId()).isEqualTo(accountId.toString());
        assertThat(item.opened()).isTrue();
        assertThat(item.creditLimit()).isEqualTo("500.00");
        assertThat(item.outstandingBalance()).isEqualTo("100.00");
        assertThat(item.authorizedAmount()).isEqualTo("25.00");
        assertThat(item.availableLimit()).isEqualTo("375.00");
        assertThat(item.projectedVersion()).isEqualTo(7L);
        assertThat(item.authorizations()).hasSize(1);
        assertThat(item.authorizations().get(0).authorizationId()).isEqualTo(authorization.authorizationId().toString());
        assertThat(item.authorizations().get(0).amount()).isEqualTo("25.00");
        assertThat(item.authorizations().get(0).status()).isEqualTo("OPEN");
        assertThat(item.authorizations().get(0).merchantName()).isEqualTo("Store");
    }

    @Test
    void acceptsMaximumPageSize() {
        when(repo.findAll(any())).thenReturn(new CreditAccountSummaryPage(List.of(), 0,
                ListCreditAccountsInput.MAX_SIZE, 0, 0));
        var output = useCase.execute(new ListCreditAccountsInput(0, ListCreditAccountsInput.MAX_SIZE));
        assertThat(output.size()).isEqualTo(ListCreditAccountsInput.MAX_SIZE);
    }
}
