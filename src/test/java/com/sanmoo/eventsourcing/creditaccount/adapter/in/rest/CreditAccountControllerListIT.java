package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.CreditAccountApplication;
import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.CreditAccountResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.PageResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.ProjectionNotReadyResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapter;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountBlocked;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {CreditAccountApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CreditAccountControllerListIT {

    @LocalServerPort
    private int port;

    @Autowired
    JdbcEventStoreAdapter eventStoreAdapter;

    @Autowired
    ProjectionWorker projectionWorker;

    private RestTemplate rest;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void eventHistory_returns404_whenAccountIsUnknown() {
        UUID id = UUID.randomUUID();
        RestTemplate errorTolerantRest = new RestTemplate();
        errorTolerantRest.setErrorHandler((response) -> false);
        ResponseEntity<Map<String, Object>> response = errorTolerantRest.exchange(
                url("/credit-accounts/" + id + "/events"), org.springframework.http.HttpMethod.GET,
                org.springframework.http.HttpEntity.EMPTY,
                new org.springframework.core.ParameterizedTypeReference<>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void eventHistory_returns400_forInvalidPageParameters() {
        UUID id = UUID.randomUUID();
        for (String query : List.of("fromVersion=0", "limit=0", "limit=201")) {
            RestTemplate errorTolerantRest = new RestTemplate();
            errorTolerantRest.setErrorHandler((response) -> false);
            ResponseEntity<Map<String, Object>> response = errorTolerantRest.exchange(
                    url("/credit-accounts/" + id + "/events?" + query), org.springframework.http.HttpMethod.GET,
                    org.springframework.http.HttpEntity.EMPTY,
                    new org.springframework.core.ParameterizedTypeReference<>() { });
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsKey("error");
        }
    }

    @Test
    void eventHistory_returns400_forMalformedAccountId() {
        RestTemplate errorTolerantRest = new RestTemplate();
        errorTolerantRest.setErrorHandler((response) -> false);
        ResponseEntity<Map<String, Object>> response = errorTolerantRest.exchange(
                url("/credit-accounts/not-a-uuid/events"), org.springframework.http.HttpMethod.GET,
                org.springframework.http.HttpEntity.EMPTY,
                new org.springframework.core.ParameterizedTypeReference<>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void eventHistory_returnsVersionedEventsFromTheWriteSide() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.parse("2025-01-01T00:00:00Z"))), Map.of());
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 1,
                List.of(new CreditLimitAssigned(CreditAccountId.of(id), Money.of("1000.00"),
                        Instant.parse("2025-01-01T00:00:01Z"))), Map.of());

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                url("/credit-accounts/" + id + "/events?limit=1"), org.springframework.http.HttpMethod.GET,
                org.springframework.http.HttpEntity.EMPTY,
                new org.springframework.core.ParameterizedTypeReference<>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("nextFromVersion")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst()).containsEntry("version", 1).containsEntry("type", "credit-account.opened.v1");
        assertThat(items.getFirst().get("payload")).isInstanceOf(Map.class);
    }

    @Test
    void getById_returns404_whenSummaryMissing() {
        UUID id = UUID.randomUUID();
        try {
            rest.getForEntity(url("/credit-accounts/" + id), CreditAccountResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void getById_returns200_withProjectedVersion() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())), Map.of());
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 1,
                List.of(new CreditLimitAssigned(CreditAccountId.of(id), Money.of("1000.00"), Instant.now())), Map.of());
        projectionWorker.processOnce(50);

        ResponseEntity<CreditAccountResponse> response = rest.getForEntity(url("/credit-accounts/" + id), CreditAccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().creditAccountId()).isEqualTo(id.toString());
        assertThat(response.getBody().projectedVersion()).isEqualTo(2L);
    }

    @Test
    void getById_returns202_whenMinVersionNotReached() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())), Map.of());

        ResponseEntity<ProjectionNotReadyResponse> response = rest.getForEntity(
                url("/credit-accounts/" + id + "?minVersion=5"),
                ProjectionNotReadyResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().requiredVersion()).isEqualTo(5L);
    }

    @Test
    void list_returns400_whenSizeAboveMax() {
        try {
            rest.getForEntity(url("/credit-accounts?size=200"), PageResponse.class);
        } catch (HttpClientErrorException.BadRequest e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void list_returns400_forUnsupportedStatus() {
        RestTemplate errorTolerantRest = new RestTemplate();
        errorTolerantRest.setErrorHandler(response -> false);
        ResponseEntity<Map<String, Object>> response = errorTolerantRest.exchange(
                url("/credit-accounts?status=UNKNOWN"), org.springframework.http.HttpMethod.GET,
                org.springframework.http.HttpEntity.EMPTY,
                new org.springframework.core.ParameterizedTypeReference<>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void list_filtersByStatusAndPaginatesFilteredResults() {
        UUID active = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();
        Instant now = Instant.now();
        eventStoreAdapter.appendEvents("CreditAccount", active.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(active), now)), Map.of());
        eventStoreAdapter.appendEvents("CreditAccount", blocked.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(blocked), now)), Map.of());
        eventStoreAdapter.appendEvents("CreditAccount", blocked.toString(), 1,
                List.of(new CreditAccountBlocked(CreditAccountId.of(blocked), "review", now.plusSeconds(1))), Map.of());
        projectionWorker.processOnce(50);

        ResponseEntity<PageResponse> firstPage = rest.getForEntity(
                url("/credit-accounts?status=BLOCKED&page=0&size=1"), PageResponse.class);
        ResponseEntity<PageResponse> beyondFilteredSet = rest.getForEntity(
                url("/credit-accounts?status=BLOCKED&page=1&size=1"), PageResponse.class);

        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPage.getBody().items()).hasSize(1);
        assertThat(firstPage.getBody().items().getFirst().creditAccountId()).isEqualTo(blocked.toString());
        assertThat(firstPage.getBody().items().getFirst().status()).isEqualTo("BLOCKED");
        assertThat(firstPage.getBody().totalItems()).isEqualTo(1);
        assertThat(firstPage.getBody().totalPages()).isEqualTo(1);
        assertThat(beyondFilteredSet.getBody().items()).isEmpty();
        assertThat(beyondFilteredSet.getBody().totalItems()).isEqualTo(1);
        assertThat(beyondFilteredSet.getBody().totalPages()).isEqualTo(1);
    }

    @Test
    void list_returnsPagedItems() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())), Map.of());
        projectionWorker.processOnce(50);

        ResponseEntity<PageResponse> response = rest.getForEntity(url("/credit-accounts"), PageResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<CreditAccountResponse> items = response.getBody().items();
        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(item -> id.toString().equals(item.creditAccountId()));
    }
}
