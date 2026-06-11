package com.sanmoo.eventsourcing.creditaccount.acceptance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Component
public class AcceptanceHttpClient {

    private final TestRestTemplate rest;
    private final String baseUrl;
    private final Duration pollingInterval;
    private final Duration pollingTimeout;

    public AcceptanceHttpClient(
            TestRestTemplate rest,
            @Value("${local.server.port}") int port,
            @Value("${acceptance.polling.interval-ms:150}") long pollingIntervalMs,
            @Value("${acceptance.polling.timeout-ms:5000}") long pollingTimeoutMs
    ) {
        this.rest = rest;
        this.baseUrl = "http://localhost:" + port + "/credit-accounts";
        this.pollingInterval = Duration.ofMillis(pollingIntervalMs);
        this.pollingTimeout = Duration.ofMillis(pollingTimeoutMs);
    }

    public Map<String, Object> openAccount() {
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl,
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    public Map<String, Object> assignCreditLimit(UUID accountId, String limit) {
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/credit-limit",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("limit", limit), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    public Map<String, Object> authorizePurchase(UUID accountId, String amount, String merchantName) {
        UUID authorizationId = UUID.randomUUID();
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/purchases/authorizations",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "authorizationId", authorizationId.toString(),
                        "amount", amount,
                        "merchantName", merchantName
                ), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("authorizationId", authorizationId.toString());
        return response.getBody();
    }

    public Map<String, Object> capturePurchase(UUID accountId, UUID authorizationId) {
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/purchases/authorizations/" + authorizationId + "/capture",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    public Map<String, Object> receivePayment(UUID accountId, String amount) {
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", amount), headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    public Map<String, Object> getSummary(UUID accountId, Long minVersion) {
        String url = baseUrl + "/" + accountId;
        if (minVersion != null) {
            url = url + "?minVersion=" + minVersion;
        }
        ResponseEntity<Map> response = rest.exchange(
                url,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    public Map<String, Object> awaitProjectedSummary(UUID accountId, long minimumVersion) {
        Instant deadline = Instant.now().plus(pollingTimeout);
        Map<String, Object> latest = null;
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<Map> response = rest.exchange(
                    baseUrl + "/" + accountId + "?minVersion=" + minimumVersion,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    Map.class
            );
            if (response.getStatusCode() == HttpStatus.OK) {
                latest = response.getBody();
                long projectedVersion = ((Number) latest.get("projectedVersion")).longValue();
                if (projectedVersion >= minimumVersion) {
                    return latest;
                }
            }
            sleep();
        }
        throw new AssertionError(
                "Timed out waiting for projected version >= " + minimumVersion
                        + " for account " + accountId + "; last response: " + latest
        );
    }

    private void sleep() {
        try {
            Thread.sleep(pollingInterval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private HttpHeaders headersWithIdempotencyKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return headers;
    }
}
