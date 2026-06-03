package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.core.port.EventStorePort;
import com.sanmoo.eventsourcing.creditaccount.core.port.IdempotencyPort;
import com.sanmoo.eventsourcing.creditaccount.application.service.CreditAccountCommandService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestConfiguration {

    @Bean
    public CreditAccountCommandService creditAccountCommandService(
            EventStorePort eventStorePort,
            IdempotencyPort idempotencyPort,
            ObjectMapper objectMapper
    ) {
        return new CreditAccountCommandService(eventStorePort, idempotencyPort, objectMapper);
    }
}
