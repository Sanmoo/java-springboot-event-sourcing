package com.sanmoo.eventsourcing.creditaccount.core.projection;

public record ProjectionWorkerResult(
        int claimed,
        int processed,
        int blocked,
        int retried,
        int failed
) {
    public static ProjectionWorkerResult empty() {
        return new ProjectionWorkerResult(0, 0, 0, 0, 0);
    }

    public ProjectionWorkerResult withClaimed(int n) {
        return new ProjectionWorkerResult(n, processed, blocked, retried, failed);
    }

    public ProjectionWorkerResult plusProcessed(int n) {
        return new ProjectionWorkerResult(claimed, processed + n, blocked, retried, failed);
    }

    public ProjectionWorkerResult plusBlocked(int n) {
        return new ProjectionWorkerResult(claimed, processed, blocked + n, retried, failed);
    }

    public ProjectionWorkerResult plusRetried(int n) {
        return new ProjectionWorkerResult(claimed, processed, blocked, retried + n, failed);
    }

    public ProjectionWorkerResult plusFailed(int n) {
        return new ProjectionWorkerResult(claimed, processed, blocked, retried, failed + n);
    }
}
