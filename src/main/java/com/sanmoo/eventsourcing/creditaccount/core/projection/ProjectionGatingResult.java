package com.sanmoo.eventsourcing.creditaccount.core.projection;

import java.util.Objects;

public record ProjectionGatingResult(
        Decision decision,
        String reason
) {
    public enum Decision {
        APPLY,
        ALREADY_APPLIED,
        BLOCKED,
        PERMANENT_FAILURE
    }

    public static ProjectionGatingResult apply() {
        return new ProjectionGatingResult(Decision.APPLY, null);
    }

    public static ProjectionGatingResult alreadyApplied() {
        return new ProjectionGatingResult(Decision.ALREADY_APPLIED, null);
    }

    public static ProjectionGatingResult blocked(String reason) {
        Objects.requireNonNull(reason, "blocked reason must not be null");
        return new ProjectionGatingResult(Decision.BLOCKED, reason);
    }

    public static ProjectionGatingResult permanentFailure(String reason) {
        Objects.requireNonNull(reason, "permanent failure reason must not be null");
        return new ProjectionGatingResult(Decision.PERMANENT_FAILURE, reason);
    }
}
