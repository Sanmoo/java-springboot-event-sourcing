package com.sanmoo.eventsourcing.creditaccount.application.result;

import java.util.Map;

public record CommandResult(String aggregateId, long aggregateVersion, Map<String, Object> responseData) {}
