package com.sanmoo.eventsourcing.creditaccount.core.projection;

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
        return new ProjectionGatingResult(Decision.BLOCKED, reason);
    }

    public static ProjectionGatingResult permanentFailure(String reason) {
        return new ProjectionGatingResult(Decision.PERMANENT_FAILURE, reason);
    }
}
