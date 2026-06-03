package com.sanmoo.eventsourcing.creditaccount.core.port;

public sealed interface IdempotencyDecision permits IdempotencyDecision.Started, IdempotencyDecision.Replay, IdempotencyDecision.Conflict {
    record Started(String key) implements IdempotencyDecision {}
    record Replay(String responsePayload) implements IdempotencyDecision {}
    record Conflict(String message) implements IdempotencyDecision {}
}
