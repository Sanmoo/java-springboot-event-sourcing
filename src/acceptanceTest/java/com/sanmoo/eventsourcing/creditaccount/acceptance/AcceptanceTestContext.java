package com.sanmoo.eventsourcing.creditaccount.acceptance;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AcceptanceTestContext {

    private UUID creditAccountId;
    private UUID authorizationId;
    private Long lastProjectedVersion;

    public UUID getCreditAccountId() { return creditAccountId; }
    public void setCreditAccountId(UUID creditAccountId) { this.creditAccountId = creditAccountId; }

    public UUID getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(UUID authorizationId) { this.authorizationId = authorizationId; }

    public Long getLastProjectedVersion() { return lastProjectedVersion; }
    public void setLastProjectedVersion(Long lastProjectedVersion) { this.lastProjectedVersion = lastProjectedVersion; }
}
