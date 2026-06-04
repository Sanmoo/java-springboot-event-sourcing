package com.sanmoo.eventsourcing.creditaccount.domain.model;

import java.util.UUID;

public record AuthorizationId(UUID value) {
    public AuthorizationId {
        java.util.Objects.requireNonNull(value, "value must not be null");
    }

    public static AuthorizationId of(UUID value) { return new AuthorizationId(value); }
}
