package com.sanmoo.eventsourcing.creditaccount.acceptance;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AcceptanceTestContext {

  private UUID creditAccountId;
  private UUID authorizationId;
  private Long lastProjectedVersion;
  private ResponseEntity<Map<String, Object>> lastResponse;
  private String lastIdempotencyKey;

  public UUID getCreditAccountId() {
    return creditAccountId;
  }

  public void setCreditAccountId(UUID creditAccountId) {
    this.creditAccountId = creditAccountId;
  }

  public UUID getAuthorizationId() {
    return authorizationId;
  }

  public void setAuthorizationId(UUID authorizationId) {
    this.authorizationId = authorizationId;
  }

  public Long getLastProjectedVersion() {
    return lastProjectedVersion;
  }

  public void setLastProjectedVersion(Long lastProjectedVersion) {
    this.lastProjectedVersion = lastProjectedVersion;
  }

  public ResponseEntity<Map<String, Object>> getLastResponse() {
    return lastResponse;
  }

  public void setLastResponse(ResponseEntity<Map<String, Object>> lastResponse) {
    this.lastResponse = lastResponse;
  }

  public String getLastIdempotencyKey() {
    return lastIdempotencyKey;
  }

  public void setLastIdempotencyKey(String lastIdempotencyKey) {
    this.lastIdempotencyKey = lastIdempotencyKey;
  }
}
