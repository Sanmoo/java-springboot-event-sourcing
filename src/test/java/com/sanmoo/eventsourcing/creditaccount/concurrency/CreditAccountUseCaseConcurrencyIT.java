package com.sanmoo.eventsourcing.creditaccount.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.AssignCreditLimitUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.AuthorizePurchaseUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.CapturePurchaseUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.GetCreditAccountUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.OpenCreditAccountUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ReceivePaymentUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ReleasePurchaseAuthorizationUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AssignCreditLimitInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CapturePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.OpenCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReceivePaymentInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReleasePurchaseAuthorizationInput;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CreditAccountUseCaseConcurrencyIT {

    @Autowired
    private OpenCreditAccountUseCase openCreditAccountUseCase;

    @Autowired
    private AssignCreditLimitUseCase assignCreditLimitUseCase;

    @Autowired
    private AuthorizePurchaseUseCase authorizePurchaseUseCase;

    @Autowired
    private CapturePurchaseUseCase capturePurchaseUseCase;

    @Autowired
    private ReleasePurchaseAuthorizationUseCase releasePurchaseAuthorizationUseCase;

    @Autowired
    private ReceivePaymentUseCase receivePaymentUseCase;

    @Autowired
    private GetCreditAccountUseCase getCreditAccountUseCase;

    @Autowired
    private ProjectionWorker projectionWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CreditAccountId accountId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM projection_checkpoints");
        jdbcTemplate.update("DELETE FROM credit_account_summary");
        jdbcTemplate.update("DELETE FROM outbox_deliveries");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM idempotency_records");
        jdbcTemplate.update("DELETE FROM event_store");

        var accountIdStr = openCreditAccountUseCase.execute(new OpenCreditAccountInput("open-key-" + UUID.randomUUID()))
                .account().creditAccountId();
        this.accountId = CreditAccountId.of(UUID.fromString(accountIdStr));
        assignCreditLimitUseCase.execute(new AssignCreditLimitInput(
                "limit-key-" + UUID.randomUUID(), accountId, Money.of("1000.00")));

        projectionWorker.processOnce(50);
    }

    @Test
    void scaffold_compiles_and_setUp_creates_account() {
        var result = getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, null));
        assertThat(result.account().creditLimit()).isEqualTo("1000.00");
    }
}
