package com.sanmoo.eventsourcing.creditaccount;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.AssignCreditLimitUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.AuthorizePurchaseUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.CapturePurchaseUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ChangeCreditLimitUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.CreditAccountUseCaseSupport;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.GetCreditAccountUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.OpenCreditAccountUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ReceivePaymentUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ReleasePurchaseAuthorizationUseCase;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CreditAccountEventSourcingApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    void useCasesAreDiscoveredWithoutRestConfiguration() {
        assertThat(applicationContext.getBean(CreditAccountUseCaseSupport.class)).isNotNull();
        assertThat(applicationContext.getBean(OpenCreditAccountUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(AssignCreditLimitUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(ChangeCreditLimitUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(AuthorizePurchaseUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(CapturePurchaseUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(ReleasePurchaseAuthorizationUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(ReceivePaymentUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(GetCreditAccountUseCase.class)).isNotNull();
        assertThat(applicationContext.containsBean("restConfiguration")).isFalse();
    }
}
