package com.sanmoo.eventsourcing.creditaccount.acceptance;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AcceptanceTestContext {

    private UUID creditAccountId;
    private UUID authorizationId;
    private Long lastProjectedVersion;
    private ResponseEntity<Map> lastResponse;
    private String lastIdempotencyKey;

    public UUID getCreditAccountId() { return creditAccountId; }
    public void setCreditAccountId(UUID creditAccountId) { this.creditAccountId = creditAccountId; }

    public UUID getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(UUID authorizationId) { this.authorizationId = authorizationId; }

    public Long getLastProjectedVersion() { return lastProjectedVersion; }
    public void setLastProjectedVersion(Long lastProjectedVersion) { this.lastProjectedVersion = lastProjectedVersion; }

    public ResponseEntity<Map> getLastResponse() { return lastResponse; }
    public void setLastResponse(ResponseEntity<Map> lastResponse) { this.lastResponse = lastResponse; }

    public String getLastIdempotencyKey() { return lastIdempotencyKey; }
    public void setLastIdempotencyKey(String lastIdempotencyKey) { this.lastIdempotencyKey = lastIdempotencyKey; }
}
