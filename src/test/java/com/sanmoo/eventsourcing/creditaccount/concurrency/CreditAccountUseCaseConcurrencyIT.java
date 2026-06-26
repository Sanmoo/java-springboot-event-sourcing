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
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CapturePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.OpenCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReceivePaymentInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReleasePurchaseAuthorizationInput;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AuthorizationNotFoundException;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import java.math.BigDecimal;
import java.util.List;
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

    private Callable<CreditAccountOutput> authorize(String idempotencyKey, String authorizationId, String amount) {
        return () -> authorizePurchaseUseCase.execute(new AuthorizePurchaseInput(
                idempotencyKey,
                accountId,
                AuthorizationId.of(UUID.fromString(authorizationId)),
                Money.of(amount),
                "Merchant"
        )).account();
    }

    @Test
    void concurrent_authorizations_on_same_aggregate_eventually_succeed() throws Exception {
        int commandCount = 5;
        var commands = new java.util.ArrayList<Callable<CreditAccountOutput>>();
        for (int i = 0; i < commandCount; i++) {
            commands.add(authorize(
                    "auth-key-" + i + "-" + UUID.randomUUID(),
                    "018f5f4b-6a3c-7000-8000-%012d".formatted(i + 1),
                    "100.00"
            ));
        }

        try (var harness = new ConcurrentCommandHarness(commandCount)) {
            ConcurrentCommandResult<CreditAccountOutput> result = harness.runConcurrently(commands, 10);

            assertThat(result.successfulResults()).hasSize(commandCount);
            assertThat(result.retryCount()).isEqualTo(result.conflictCount());

            projectionWorker.processOnce(250);

            var finalAccount = getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, null));
            assertThat(finalAccount.account().authorizedAmount()).isEqualTo("500.00");
            assertThat(finalAccount.account().availableLimit()).isEqualTo("500.00");
            assertThat(new BigDecimal(finalAccount.account().authorizedAmount())
                    .add(new BigDecimal(finalAccount.account().outstandingBalance())))
                    .isLessThanOrEqualTo(new BigDecimal("1000.00"));
        }
    }

    @Test
    void concurrent_duplicate_idempotent_requests_process_once_and_replay() throws Exception {
        String authorizationId = "018f5f4b-6a3c-7000-8000-000000000111";
        String idempotencyKey = "duplicate-key-" + UUID.randomUUID();
        var input = new AuthorizePurchaseInput(
                idempotencyKey,
                accountId,
                AuthorizationId.of(UUID.fromString(authorizationId)),
                Money.of("75.00"),
                "Merchant"
        );

        Callable<AuthorizePurchaseOutput> command =
                () -> authorizePurchaseUseCase.execute(input);

        try (var harness = new ConcurrentCommandHarness(3)) {
            ConcurrentCommandResult<AuthorizePurchaseOutput> result =
                    harness.runConcurrently(List.of(command, command, command), 10);

            assertThat(result.successfulResults()).hasSize(3);

            long replayedCount = result.successfulResults().stream().filter(
                    AuthorizePurchaseOutput::replayed).count();
            assertThat(replayedCount).isEqualTo(2);

            var first = result.successfulResults().getFirst();
            assertThat(result.successfulResults())
                    .allMatch(output -> output.authorizationId().equals(first.authorizationId()));

            projectionWorker.processOnce(250);

            var finalAccount = getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, null));
            assertThat(finalAccount.account().authorizedAmount()).isEqualTo("75.00");
        }
    }

    @Test
    void concurrent_authorization_and_capture_eventually_leaves_captured_state() throws Exception {
        String authorizationId = "018f5f4b-6a3c-7000-8000-000000000999";

        Callable<CreditAccountOutput> authorizeCommand = authorize(
                "auth-capture-key-auth", authorizationId, "150.00");
        Callable<CreditAccountOutput> captureCommand = () -> {
            Exception lastError = null;
            for (int i = 0; i < 20; i++) {
                try {
                    return capturePurchaseUseCase.execute(new CapturePurchaseInput(
                            "auth-capture-key-capture",
                            accountId,
                            AuthorizationId.of(UUID.fromString(authorizationId))
                    )).account();
                } catch (AuthorizationNotFoundException e) {
                    lastError = e;
                    Thread.sleep(50);
                }
            }
            throw new RuntimeException("Capture failed after retries", lastError);
        };

        try (var harness = new ConcurrentCommandHarness(2)) {
            ConcurrentCommandResult<CreditAccountOutput> result = harness.runConcurrently(
                    List.of(authorizeCommand, captureCommand), 10);

            assertThat(result.successfulResults()).hasSize(2);

            projectionWorker.processOnce(250);

            var finalAccount = getCreditAccountUseCase.execute(new GetCreditAccountInput(accountId, null));
            assertThat(finalAccount.account().outstandingBalance()).isEqualTo("150.00");
            assertThat(finalAccount.account().authorizedAmount()).isEqualTo("0.00");
        }
    }
}
