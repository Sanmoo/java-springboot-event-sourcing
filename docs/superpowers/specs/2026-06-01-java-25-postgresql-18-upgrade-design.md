# Java 25 LTS and PostgreSQL 18 Upgrade Design

## Summary

Upgrade the project from Java 21 and PostgreSQL 17 to the current target versions:

- Java 25 LTS
- PostgreSQL 18

This is a focused runtime/dependency-alignment change. It does not change the event-sourcing design, REST API, domain model, database schema, or application behavior.

## Goals

- Build and test the project with Java 25 via the Gradle Java toolchain.
- Run PostgreSQL integration tests against PostgreSQL 18 containers.
- Update documentation so the declared runtime requirements match the actual project configuration.
- Remove remaining project references to Java 21 and PostgreSQL 17.

## Non-Goals

- Do not add Gradle Java toolchain auto-provisioning.
- Do not change Spring Boot, Gradle, Liquibase, Testcontainers, or PostgreSQL JDBC dependency versions unless required by the Java/PostgreSQL upgrade.
- Do not modify domain behavior, event formats, REST contracts, or database migrations.
- Do not introduce production Docker Compose or deployment configuration.

## Java Upgrade

Update `build.gradle.kts` to require Java 25:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

The project will require JDK 25 to be installed locally. Gradle toolchain auto-provisioning will not be configured.

## PostgreSQL Upgrade

Update Testcontainers usage from `postgres:17` to `postgres:18`.

To avoid duplicated image literals and future drift, introduce this shared test helper:

- `src/test/java/com/sanmoo/eventsourcing/creditaccount/PostgresTestImage.java`

```java
public final class PostgresTestImage {
    public static final DockerImageName POSTGRES_18 =
            DockerImageName.parse("postgres:18");

    private PostgresTestImage() {
    }
}
```

Use this constant in all current PostgreSQL container definitions:

- `src/test/java/com/sanmoo/eventsourcing/creditaccount/TestcontainersConfiguration.java`
- `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java`

The tag should remain `postgres:18` rather than a fixed patch tag. This keeps the practice repository aligned with the latest PostgreSQL 18 patch release while targeting the PostgreSQL 18 major version.

## Documentation Updates

Update `README.md` to state:

- The project uses Java 25 LTS.
- The project uses PostgreSQL 18 for integration tests.
- Running tests requires Docker and a locally installed JDK 25.

## Verification

Run:

```bash
./gradlew clean test
```

Success criteria:

- The project compiles with Java 25 configured by the Gradle toolchain.
- All tests pass.
- No project files still reference Java 21 as the required runtime.
- No project files still reference `postgres:17`.

## Risk Assessment

This upgrade is low risk because the current code uses standard Java language features and PostgreSQL features that remain compatible with PostgreSQL 18. The main expected failure mode is environmental: if JDK 25 is not installed locally, Gradle toolchain resolution will fail. That failure is acceptable and should be documented because auto-provisioning is intentionally out of scope.
