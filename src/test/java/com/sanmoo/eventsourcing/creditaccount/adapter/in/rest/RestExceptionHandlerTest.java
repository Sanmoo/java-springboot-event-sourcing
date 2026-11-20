package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.ErrorResponse;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountAlreadyBlockedException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountBlockedException;
import com.sanmoo.eventsourcing.creditaccount.domain.error.AccountNotBlockedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void blockingRuleExceptionsReturn422WithTheirMessages() {
        assertUnprocessable(handler.handleDomainException(
                new AccountBlockedException("credit account is blocked")), "credit account is blocked");
        assertUnprocessable(handler.handleDomainException(
                new AccountAlreadyBlockedException("credit account is already blocked")), "credit account is already blocked");
        assertUnprocessable(handler.handleDomainException(
                new AccountNotBlockedException("credit account is not blocked")), "credit account is not blocked");
    }

    private void assertUnprocessable(ResponseEntity<ErrorResponse> response, String message) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse(message));
    }
}
