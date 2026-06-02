package com.sanmoo.eventsourcing.creditaccount.application.result;

import java.util.Map;

public record CommandResult(String aggregateId, long aggregateVersion, Map<String, Object> responseData, boolean replayed) {

    public CommandResult(String aggregateId, long aggregateVersion, Map<String, Object> responseData) {
        this(aggregateId, aggregateVersion, responseData, false);
    }
}
