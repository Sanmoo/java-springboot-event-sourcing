package com.sanmoo.eventsourcing.creditaccount.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.CreditAccountApplication;
import com.sanmoo.eventsourcing.creditaccount.TestcontainersConfiguration;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.CreditAccountResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.PageResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.in.rest.dto.ProjectionNotReadyResponse;
import com.sanmoo.eventsourcing.creditaccount.adapter.out.postgres.JdbcEventStoreAdapter;
import com.sanmoo.eventsourcing.creditaccount.core.projection.ProjectionWorker;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@SpringBootTest(
    classes = {CreditAccountApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CreditAccountControllerListIT {

  @LocalServerPort private int port;

  @Autowired JdbcEventStoreAdapter eventStoreAdapter;

  @Autowired ProjectionWorker projectionWorker;

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
      rest.getForEntity(url("/credit-accounts/" + id), CreditAccountResponse.class);
    } catch (HttpClientErrorException.NotFound e) {
      assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Test
  void getById_returns200_withProjectedVersion() {
    UUID id = UUID.randomUUID();
    eventStoreAdapter.appendEvents(
        "CreditAccount",
        id.toString(),
        0,
        List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())),
        Map.of());
    eventStoreAdapter.appendEvents(
        "CreditAccount",
        id.toString(),
        1,
        List.of(
            new CreditLimitAssigned(CreditAccountId.of(id), Money.of("1000.00"), Instant.now())),
        Map.of());
    projectionWorker.processOnce(50);

    ResponseEntity<CreditAccountResponse> response =
        rest.getForEntity(url("/credit-accounts/" + id), CreditAccountResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().creditAccountId()).isEqualTo(id.toString());
    assertThat(response.getBody().projectedVersion()).isEqualTo(2L);
  }

  @Test
  void getById_returns202_whenMinVersionNotReached() {
    UUID id = UUID.randomUUID();
    eventStoreAdapter.appendEvents(
        "CreditAccount",
        id.toString(),
        0,
        List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())),
        Map.of());

    ResponseEntity<ProjectionNotReadyResponse> response =
        rest.getForEntity(
            url("/credit-accounts/" + id + "?minVersion=5"), ProjectionNotReadyResponse.class);
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
  void list_returnsPagedItems() {
    UUID id = UUID.randomUUID();
    eventStoreAdapter.appendEvents(
        "CreditAccount",
        id.toString(),
        0,
        List.of(new CreditAccountOpened(CreditAccountId.of(id), Instant.now())),
        Map.of());
    projectionWorker.processOnce(50);

    ResponseEntity<PageResponse> response =
        rest.getForEntity(url("/credit-accounts"), PageResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<CreditAccountResponse> items = response.getBody().items();
    assertThat(items).isNotEmpty();
    assertThat(items).anyMatch(item -> id.toString().equals(item.creditAccountId()));
  }
}
