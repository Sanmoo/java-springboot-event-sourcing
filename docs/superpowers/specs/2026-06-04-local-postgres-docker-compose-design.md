# Local PostgreSQL Docker Compose Design

## Goal

Make local development easier by providing a Docker Compose setup for PostgreSQL 18 that matches the application's existing datasource configuration. Developers should be able to start the database container and then run the Spring Boot application locally without additional configuration.

## Context

The application currently connects to PostgreSQL at:

- URL: `jdbc:postgresql://localhost:5432/credit_account`
- Username: `credit_account`
- Password: `credit_account`

Liquibase manages schema creation when the application starts.

## Design

Add a `docker-compose.yml` file at the repository root with one service: `postgres`.

The service will use the `postgres:18` image and expose container port `5432` on host port `5432`, preserving compatibility with `src/main/resources/application.yml`.

The service will set these environment variables:

- `POSTGRES_DB=credit_account`
- `POSTGRES_USER=credit_account`
- `POSTGRES_PASSWORD=credit_account`

A named Docker volume will persist PostgreSQL data between container restarts.

A healthcheck using `pg_isready` will allow developers and tooling to confirm when PostgreSQL is ready to accept connections.

## Documentation

Update `README.md` with a short local development section:

1. Start PostgreSQL with `docker compose up -d`.
2. Run the application locally.
3. Stop PostgreSQL with `docker compose down`.
4. Optionally remove persisted data with `docker compose down -v`.

## Error Handling and Constraints

If host port `5432` is already in use, Docker Compose will fail to start the service. The README will mention that developers can adjust the published host port if needed, but the default will remain `5432` to keep local application startup zero-config.

## Testing

Validate the compose file with `docker compose config`.

Optionally, if Docker is available, start the service with `docker compose up -d`, check its health, and stop it with `docker compose down`.
