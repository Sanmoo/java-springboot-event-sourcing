# Local PostgreSQL Docker Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Docker Compose PostgreSQL 18 service that lets developers start the database and run the Spring Boot application locally without extra configuration.

**Architecture:** Add a root-level Compose file with a single PostgreSQL service matching `src/main/resources/application.yml`. Persist data in a named Docker volume and document the local development workflow in `README.md`.

**Tech Stack:** Docker Compose, PostgreSQL 18, Spring Boot, Liquibase, Gradle Kotlin DSL.

---

## File Structure

- Create: `docker-compose.yml`
  - Defines the local PostgreSQL 18 development database.
  - Publishes `5432:5432` to match the existing Spring datasource URL.
  - Uses a named volume for persistent database data.
  - Adds a `pg_isready` healthcheck.
- Modify: `README.md`
  - Adds local development instructions for starting/stopping PostgreSQL before running the app.
  - Documents how to remove persisted data and what to do if port `5432` is already occupied.

---

### Task 1: Add PostgreSQL Docker Compose Service

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: Create the compose file**

Create `docker-compose.yml` with this exact content:

```yaml
services:
  postgres:
    image: postgres:18
    container_name: credit-account-postgres
    environment:
      POSTGRES_DB: credit_account
      POSTGRES_USER: credit_account
      POSTGRES_PASSWORD: credit_account
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U credit_account -d credit_account"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

volumes:
  postgres-data:
```

- [ ] **Step 2: Validate Docker Compose syntax**

Run:

```bash
docker compose config
```

Expected: command exits with status `0` and prints normalized Compose configuration including `services.postgres` and `volumes.postgres-data`.

- [ ] **Step 3: Commit the compose file**

Run:

```bash
git add docker-compose.yml
git commit -m "chore: add local postgres compose service"
```

Expected: commit succeeds and includes only `docker-compose.yml`.

---

### Task 2: Document Local Development Workflow

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add local development instructions**

In `README.md`, replace the current `## Running` section:

```markdown
## Running

```bash
# Tests require:
# - JDK 25 installed locally
# - Docker for Testcontainers
./gradlew test
```
```

with this content:

````markdown
## Running

### Local development

Start PostgreSQL 18:

```bash
docker compose up -d
```

Then run the Spring Boot application locally. The default datasource configuration points to the Compose database at `localhost:5432/credit_account` using username and password `credit_account`.

Stop PostgreSQL:

```bash
docker compose down
```

Remove the persisted database volume when you want a clean database:

```bash
docker compose down -v
```

If port `5432` is already in use on your machine, change the host side of the port mapping in `docker-compose.yml` and update the local datasource URL accordingly.

### Tests

Tests require:

- JDK 25 installed locally
- Docker for Testcontainers

```bash
./gradlew test
```
````

- [ ] **Step 2: Verify README renders expected sections**

Run:

```bash
grep -n "Local development\|docker compose up -d\|docker compose down -v\|Docker for Testcontainers" README.md
```

Expected: output includes lines for all four searched strings.

- [ ] **Step 3: Commit README changes**

Run:

```bash
git add README.md
git commit -m "docs: document local postgres development"
```

Expected: commit succeeds and includes only `README.md`.

---

### Task 3: Final Verification

**Files:**
- Verify: `docker-compose.yml`
- Verify: `README.md`

- [ ] **Step 1: Validate Compose configuration again**

Run:

```bash
docker compose config
```

Expected: command exits with status `0`.

- [ ] **Step 2: Check working tree status**

Run:

```bash
git status --short
```

Expected: no output.

- [ ] **Step 3: Optional runtime smoke test if Docker daemon is available**

Run:

```bash
docker compose up -d
```

Expected: PostgreSQL container starts.

Then run:

```bash
docker compose ps
```

Expected: `credit-account-postgres` is listed and eventually reports a healthy or running state.

Clean up with:

```bash
docker compose down
```

Expected: container stops and the named volume remains for future development sessions.
