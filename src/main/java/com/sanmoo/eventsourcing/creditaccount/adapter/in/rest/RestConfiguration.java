package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestConfiguration {

    @Bean
    public CreditAccountUseCaseSupport creditAccountUseCaseSupport(
            EventStorePort eventStorePort,
            IdempotencyPort idempotencyPort,
            ObjectMapper objectMapper
    ) {
        return new CreditAccountUseCaseSupport(eventStorePort, idempotencyPort, objectMapper);
    }

    @Bean
    public OpenCreditAccountUseCase openCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        return new OpenCreditAccountUseCase(support);
    }

    @Bean
    public AssignCreditLimitUseCase assignCreditLimitUseCase(CreditAccountUseCaseSupport support) {
        return new AssignCreditLimitUseCase(support);
    }

    @Bean
    public ChangeCreditLimitUseCase changeCreditLimitUseCase(CreditAccountUseCaseSupport support) {
        return new ChangeCreditLimitUseCase(support);
    }

    @Bean
    public AuthorizePurchaseUseCase authorizePurchaseUseCase(CreditAccountUseCaseSupport support) {
        return new AuthorizePurchaseUseCase(support);
    }

    @Bean
    public CapturePurchaseUseCase capturePurchaseUseCase(CreditAccountUseCaseSupport support) {
        return new CapturePurchaseUseCase(support);
    }

    @Bean
    public ReleasePurchaseAuthorizationUseCase releasePurchaseAuthorizationUseCase(CreditAccountUseCaseSupport support) {
        return new ReleasePurchaseAuthorizationUseCase(support);
    }

    @Bean
    public ReceivePaymentUseCase receivePaymentUseCase(CreditAccountUseCaseSupport support) {
        return new ReceivePaymentUseCase(support);
    }

    @Bean
    public GetCreditAccountUseCase getCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        return new GetCreditAccountUseCase(support);
    }
}
