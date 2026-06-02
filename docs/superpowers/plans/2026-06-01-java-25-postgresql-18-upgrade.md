# Java 25 and PostgreSQL 18 Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the project runtime requirements from Java 21 and PostgreSQL 17 to Java 25 LTS and PostgreSQL 18.

**Architecture:** The Gradle Java toolchain drives the compile target. Testcontainers PostgreSQL container image drives the integration test database. A shared test helper centralizes the PostgreSQL Docker image literal. The README documents the new runtime requirements.

**Tech Stack:** Gradle Kotlin DSL, Java 25 LTS, Spring Boot 4.0.6, Testcontainers, PostgreSQL 18 Docker image.

---

### Task 1: Upgrade Java toolchain to 25

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Change Gradle Java toolchain version**

  In `build.gradle.kts`, locate the `java.toolchain` block:

  ```kotlin
  java {
      toolchain {
          languageVersion = JavaLanguageVersion.of(21)
      }
  }
  ```

  Change to:

  ```kotlin
  java {
      toolchain {
          languageVersion = JavaLanguageVersion.of(25)
      }
  }
  ```

- [ ] **Step 2: Verify the project still compiles**

  Run:
  ```bash
  ./gradlew compileJava
  ```

  Expected: `BUILD SUCCESSFUL` if JDK 25 is installed locally. If JDK 25 is not available, the toolchain resolution will fail — this is expected and will be documented in the README.

- [ ] **Step 3: Commit**

  ```bash
  git add build.gradle.kts
  git commit -m "build: upgrade Java toolchain from 21 to 25 LTS"
  ```

---

### Task 2: Upgrade PostgreSQL test containers to 18

**Files:**
- Create: `src/test/java/com/sanmoo/eventsourcing/creditaccount/PostgresTestImage.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/TestcontainersConfiguration.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java`

- [ ] **Step 1: Create shared PostgreSQL Docker image constant**

  Create `src/test/java/com/sanmoo/eventsourcing/creditaccount/PostgresTestImage.java`:

  ```java
  package com.sanmoo.eventsourcing.creditaccount;

  import org.testcontainers.utility.DockerImageName;

  public final class PostgresTestImage {
      public static final DockerImageName POSTGRES_18 =
              DockerImageName.parse("postgres:18");

      private PostgresTestImage() {
      }
  }
  ```

- [ ] **Step 2: Replace `postgres:17` in TestcontainersConfiguration**

  In `src/test/java/com/sanmoo/eventsourcing/creditaccount/TestcontainersConfiguration.java`, change:

  ```java
  @Bean
  @ServiceConnection
  public PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));
  }
  ```

  To:

  ```java
  @Bean
  @ServiceConnection
  public PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>(PostgresTestImage.POSTGRES_18);
  }
  ```

  Remove the unused import for `DockerImageName` if no other usage remains.

- [ ] **Step 3: Replace `postgres:17` in JdbcEventStoreAdapterIT**

  In `src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java`, change:

  ```java
  new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"))
  ```

  To:

  ```java
  new PostgreSQLContainer<>(PostgresTestImage.POSTGRES_18)
  ```

  Remove the unused import for `DockerImageName` if no other usage remains.

- [ ] **Step 4: Run tests to verify PostgreSQL 18 works**

  Run:
  ```bash
  ./gradlew test --tests '*JdbcEventStoreAdapterIT' --tests '*CreditAccountControllerIT'
  ```

  Expected: all tests pass with `postgres:18`.

- [ ] **Step 5: Commit**

  ```bash
  git add src/test/java/com/sanmoo/eventsourcing/creditaccount/PostgresTestImage.java
  git add src/test/java/com/sanmoo/eventsourcing/creditaccount/TestcontainersConfiguration.java
  git add src/test/java/com/sanmoo/eventsourcing/creditaccount/adapter/out/postgres/JdbcEventStoreAdapterIT.java
  git commit -m "test: upgrade PostgreSQL testcontainers from 17 to 18"
  ```

---

### Task 3: Update README to document Java 25 and PostgreSQL 18

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the Java version in the header**

  Change:
  ```
  - **Java 21** + **Spring Boot 4.0.6**
  ```

  To:
  ```
  - **Java 25 LTS** + **Spring Boot 4.0.6**
  ```

- [ ] **Step 2: Update the Running section**

  Change:
  ```bash
  # Tests (uses Testcontainers — requires Docker)
  ./gradlew test
  ```

  To:
  ```bash
  # Tests require:
  # - JDK 25 installed locally
  # - Docker for Testcontainers
  ./gradlew test
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add README.md
  git commit -m "docs: update README for Java 25 and PostgreSQL 18"
  ```

---

### Task 4: Final verification

**Files:**
- None new or modified.

- [ ] **Step 1: Run full test suite**

  Run:
  ```bash
  ./gradlew clean test
  ```

  Expected: `BUILD SUCCESSFUL` with all 34 tests passing.

- [ ] **Step 2: Scan for stale references to Java 21**

  Run:
  ```bash
  grep -r "Java 21\|JavaLanguageVersion.of(21)" --include="*.md" --include="*.kts" --include="*.java" .
  ```

  Expected: zero matches outside of git history.

- [ ] **Step 3: Scan for stale references to postgres:17**

  Run:
  ```bash
  grep -r "postgres:17" --include="*.md" --include="*.kts" --include="*.java" .
  ```

  Expected: zero matches outside of git history.

- [ ] **Step 4: Commit if any stray changes remain**

  ```bash
  git add -A
  git diff --cached --quiet || git commit -m "chore: final verification cleanup"
  ```
