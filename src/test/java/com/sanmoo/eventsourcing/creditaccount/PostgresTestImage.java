package com.sanmoo.eventsourcing.creditaccount;

import org.testcontainers.utility.DockerImageName;

public final class PostgresTestImage {
    public static final DockerImageName POSTGRES_18 =
            DockerImageName.parse("postgres:18");

    private PostgresTestImage() {
    }
}
