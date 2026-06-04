package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CreditAccountControllerIT {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        baseUrl = "http://localhost:" + port + "/credit-accounts";
    }

    @Test
    void fullHappyPath() {
        // 1. POST /credit-accounts
        var openResponse = restTemplate.postForEntity(
                baseUrl,
                createRequest(null),
                Map.class
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accountId = (String) openResponse.getBody().get("creditAccountId");

        // 2. POST /credit-accounts/{id}/credit-limit
        var limitResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/credit-limit",
                new HttpEntity<>(Map.of("limit", "500.00"), createHeaders()),
                Map.class
        );
        assertThat(limitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. POST /credit-accounts/{id}/purchases/authorizations
        var authResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/purchases/authorizations",
                new HttpEntity<>(Map.of(
                        "amount", "100.00",
                        "merchantName", "Store"
                ), createHeaders()),
                Map.class
        );
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String authorizationId = (String) authResponse.getBody().get("authorizationId");

        // 4. GET /credit-accounts/{id} (verify available limit decreased)
        var getAfterAuth = restTemplate.getForEntity(
                baseUrl + "/" + accountId,
                Map.class
        );
        assertThat(getAfterAuth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterAuth.getBody()).containsEntry("availableLimit", "400.00");

        // 5. POST capture
        var captureResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/purchases/authorizations/" + authorizationId + "/capture",
                createRequest(null),
                Map.class
        );
        assertThat(captureResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 6. GET check outstanding balance
        var getAfterCapture = restTemplate.getForEntity(
                baseUrl + "/" + accountId,
                Map.class
        );
        assertThat(getAfterCapture.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterCapture.getBody()).containsEntry("outstandingBalance", "100.00");

        // 7. POST payment
        var paymentResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/payments",
                new HttpEntity<>(Map.of("amount", "50.00"), createHeaders()),
                Map.class
        );
        assertThat(paymentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 8. GET check outstanding balance reduced
        var getAfterPayment = restTemplate.getForEntity(
                baseUrl + "/" + accountId,
                Map.class
        );
        assertThat(getAfterPayment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterPayment.getBody()).containsEntry("outstandingBalance", "50.00");
    }

    @Test
    void authorizesAndReleasesUsingReturnedAuthorizationId() {
        var openResponse = restTemplate.postForEntity(baseUrl, createRequest(null), Map.class);
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accountId = (String) openResponse.getBody().get("creditAccountId");

        var limitResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/credit-limit",
                new HttpEntity<>(Map.of("limit", "500.00"), createHeaders()),
                Map.class
        );
        assertThat(limitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var authResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/purchases/authorizations",
                new HttpEntity<>(Map.of(
                        "amount", "100.00",
                        "merchantName", "Store"
                ), createHeaders()),
                Map.class
        );
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String authorizationId = (String) authResponse.getBody().get("authorizationId");
        assertThat(authorizationId).isNotBlank();

        var releaseResponse = restTemplate.postForEntity(
                baseUrl + "/" + accountId + "/purchases/authorizations/" + authorizationId + "/release",
                createRequest(null),
                Map.class
        );
        assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var getAfterRelease = restTemplate.getForEntity(baseUrl + "/" + accountId, Map.class);
        assertThat(getAfterRelease.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAfterRelease.getBody()).containsEntry("authorizedAmount", "0.00");
        assertThat(getAfterRelease.getBody()).containsEntry("availableLimit", "500.00");
    }

    @Test
    void idempotencyKeyReturnsSameResult() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set("Idempotency-Key", "fixed-key-123");
        var request = new HttpEntity<>(Map.of("limit", "500.00"), headers);

        // First call creates the account
        var response1 = restTemplate.postForEntity(
                baseUrl,
                request,
                Map.class
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second call with same key returns same result
        var response2 = restTemplate.postForEntity(
                baseUrl,
                request,
                Map.class
        );
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody()).isEqualTo(response1.getBody());
    }

    private HttpEntity<?> createRequest(Object body) {
        return new HttpEntity<>(body, createHeaders());
    }

    private HttpHeaders createHeaders() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return headers;
    }
}
