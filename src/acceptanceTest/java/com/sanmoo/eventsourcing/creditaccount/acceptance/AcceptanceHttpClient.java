package com.sanmoo.eventsourcing.creditaccount.acceptance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class AcceptanceHttpClient {

    @Autowired
    private Environment environment;

    @Autowired
    private AcceptanceTestContext context;

    @Value("${acceptance.polling.interval-ms:150}")
    private long pollingIntervalMs;

    @Value("${acceptance.polling.timeout-ms:5000}")
    private long pollingTimeoutMs;

    private RestTemplate rest;
    private String baseUrl;
    private Duration pollingInterval;
    private Duration pollingTimeout;
    private boolean initialized;

    private void ensureInitialized() {
        if (initialized) return;
        this.rest = new RestTemplate();
        this.rest.setErrorHandler((ClientHttpResponse response) -> false);
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        this.baseUrl = "http://localhost:" + port + "/credit-accounts";
        this.pollingInterval = Duration.ofMillis(pollingIntervalMs);
        this.pollingTimeout = Duration.ofMillis(pollingTimeoutMs);
        this.initialized = true;
    }

    public Map<String, Object> openAccount() {
        ensureInitialized();
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl,
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), headers),
                Map.class
        );
        context.setLastResponse(response);
        return response.getBody();
    }

    public Map<String, Object> assignCreditLimit(UUID accountId, String limit) {
        ensureInitialized();
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/credit-limit",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("limit", limit), headers),
                Map.class
        );
        context.setLastResponse(response);
        return response.getBody();
    }

    public Map<String, Object> authorizePurchase(UUID accountId, String amount, String merchantName) {
        ensureInitialized();
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
        context.setLastResponse(response);
        return response.getBody();
    }

    public Map<String, Object> capturePurchase(UUID accountId, UUID authorizationId) {
        ensureInitialized();
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/purchases/authorizations/" + authorizationId + "/capture",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), headers),
                Map.class
        );
        context.setLastResponse(response);
        return response.getBody();
    }

    public Map<String, Object> receivePayment(UUID accountId, String amount) {
        ensureInitialized();
        HttpHeaders headers = headersWithIdempotencyKey();
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", amount), headers),
                Map.class
        );
        context.setLastResponse(response);
        return response.getBody();
    }

    public Map<String, Object> getSummary(UUID accountId, Long minVersion) {
        ensureInitialized();
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
        context.setLastResponse(response);
        return response.getBody();
    }

    public Map<String, Object> awaitProjectedSummary(UUID accountId, long minimumVersion) {
        ensureInitialized();
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
                    context.setLastResponse(response);
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

    public Map<String, Object> assignCreditLimitWithKey(UUID accountId, String limit, String idempotencyKey) {
        ensureInitialized();
        HttpHeaders headers = headersWithIdempotencyKey();
        headers.set("Idempotency-Key", idempotencyKey);
        ResponseEntity<Map> response = rest.exchange(
                baseUrl + "/" + accountId + "/credit-limit",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("limit", limit), headers),
                Map.class
        );
        context.setLastResponse(response);
        return response.getBody();
    }

    public ResponseEntity<Map> getSummaryRaw(UUID accountId, Long minVersion) {
        ensureInitialized();
        String url = baseUrl + "/" + accountId;
        if (minVersion != null) {
            url = url + "?minVersion=" + minVersion;
        }
        Instant deadline = Instant.now().plus(pollingTimeout);
        ResponseEntity<Map> response = null;
        while (Instant.now().isBefore(deadline)) {
            response = rest.exchange(
                    url,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    Map.class
            );
            if (response.getStatusCode() == HttpStatus.OK) {
                context.setLastResponse(response);
                return response;
            }
            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = response.getBody();
                if (body != null && body.get("currentProjectionVersion") != null) {
                    context.setLastResponse(response);
                    return response;
                }
            }
            sleep();
        }
        if (response != null) {
            context.setLastResponse(response);
        }
        return response;
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
