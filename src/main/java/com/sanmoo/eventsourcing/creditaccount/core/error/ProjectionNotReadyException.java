package com.sanmoo.eventsourcing.creditaccount.core.error;

public class ProjectionNotReadyException extends RuntimeException {
    private final java.util.UUID creditAccountId;
    private final Long currentProjectionVersion;
    private final long requiredVersion;

    public ProjectionNotReadyException(java.util.UUID creditAccountId, Long currentProjectionVersion, long requiredVersion) {
        super("Projection not ready for " + creditAccountId);
        this.creditAccountId = creditAccountId;
        this.currentProjectionVersion = currentProjectionVersion;
        this.requiredVersion = requiredVersion;
    }

    public java.util.UUID getCreditAccountId() { return creditAccountId; }
    public Long getCurrentProjectionVersion() { return currentProjectionVersion; }
    public long getRequiredVersion() { return requiredVersion; }
}
