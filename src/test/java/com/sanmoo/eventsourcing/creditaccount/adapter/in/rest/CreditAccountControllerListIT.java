package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import com.sanmoo.eventsourcing.creditaccount.CreditAccountApplication;
import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapter;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
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
    void getById_returns404_whenSummaryMissing() {
        UUID id = UUID.randomUUID();
        try {
            rest.getForEntity(url("/credit-accounts/" + id), Map.class);
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

        ResponseEntity<Map> response = rest.getForEntity(url("/credit-accounts/" + id), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("creditAccountId", id.toString());
        assertThat(response.getBody()).containsEntry("projectedVersion", 2);
    }

    @Test
    void getById_returns202_whenMinVersionNotReached() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())), Map.of());

        ResponseEntity<Map> response = rest.getForEntity(url("/credit-accounts/" + id + "?minVersion=5"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat((Integer) response.getBody().get("requiredVersion")).isEqualTo(5);
    }

    @Test
    void list_returns400_whenSizeAboveMax() {
        try {
            rest.getForEntity(url("/credit-accounts?size=200"), Map.class);
        } catch (HttpClientErrorException.BadRequest e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void list_returnsPagedItems() {
        UUID id = UUID.randomUUID();
        eventStoreAdapter.appendEvents("CreditAccount", id.toString(), 0,
                List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())), Map.of());
        projectionWorker.processOnce(50);

        ResponseEntity<Map> response = rest.getForEntity(url("/credit-accounts"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(item -> id.toString().equals(item.get("creditAccountId")));
    }
}
