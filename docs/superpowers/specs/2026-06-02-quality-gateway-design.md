# Quality Gateway Design

Date: 2026-06-02

## Summary

Add a comprehensive quality gateway to the project combining architectural fitness functions (ArchUnit), mutation testing (PITest), and static code analysis (Checkstyle, PMD, SpotBugs, Error Prone). All checks are blocking — any violation fails the build.

## Goals

- Enforce architectural invariants via ArchUnit fitness functions in a dedicated source set.
- Verify test effectiveness via mutation testing on domain (100%) and application (80%) unit tests.
- Catch bugs, style violations, and code smells via static analysis at compile time and bytecode level.
- Integrate all checks into `./gradlew check` so the quality gateway runs on every build.

## Non-Goals

- Do not add SonarQube or any server-based analysis platform.
- Do not configure CI/CD pipelines — this is a local build quality gateway.
- Do not add mutation testing for integration tests (Testcontainers-based).
- Do not create custom Gradle convention plugins in `buildSrc/`.
- Do not add Checkstyle/PMD/SpotBugs custom rule configs — use tool defaults initially.

## Structure

### Source Sets

```
src/
├── main/java/                          # Production code (unchanged)
├── test/java/                          # Functional tests (unchanged)
│   └── com/sanmoo/eventsourcing/creditaccount/
│       ├── domain/CreditAccountTest.java
│       ├── application/service/CreditAccountCommandServiceTest.java
│       ├── adapter/in/rest/CreditAccountControllerIT.java
│       └── adapter/out/postgres/...
│
└── qualityTest/java/                   # NEW — Fitness functions (ArchUnit)
    └── com/sanmoo/eventsourcing/creditaccount/quality/
        ├── ArchitectureFitnessFunctions.java
        ├── NamingConventionFitnessFunctions.java
        └── DesignRulesFitnessFunctions.java
```

The `qualityTest` source set depends on `main` and uses `testImplementation` dependencies. It runs as part of `check`, after `test`.

### Configuration Directory

```
config/
├── checkstyle/     # Empty initially — using defaults
├── pmd/            # Empty initially — using defaults
└── spotbugs/       # Empty initially — using defaults
```

Reserved for future custom rule configs. Currently unused since all tools use their default rule sets.

## ArchUnit — Fitness Functions

### ArchitectureFitnessFunctions.java

Verifies layer isolation:

- **Domain has no external dependencies** — classes in `domain` do not depend on Spring, JDBC, Jackson, or any package outside `domain` and `java.*`.
- **Application does not depend on adapters** — `application` only depends on `domain` and `java.*`, never on `adapter.in` or `adapter.out`.
- **Adapters do not depend on each other** — `adapter.in` does not depend on `adapter.out` and vice-versa.
- **Application ports are interfaces** — all types in `application.port` must be interfaces (except DTOs/records like `AppendResult`, `EventEnvelope`, `IdempotencyDecision`).
- **Domain events are records** — all classes in `domain.event` must be Java records.
- **Domain model value objects are records** — all classes in `domain.model` must be Java records.

### NamingConventionFitnessFunctions.java

Verifies package structure and naming:

- **Packages follow the project convention** — `adapter.in.rest`, `adapter.out.postgres`, `application.command`, `application.service`, `application.port`, `application.result`, `application.error`, `domain.event`, `domain.model`, `domain.error`.
- **Commands are records** — all classes in `application.command` are Java records.
- **Commands have `Command` suffix** — every class in `application.command` ends with `Command`.
- **Events follow domain naming** — classes in `domain.event` follow the established naming (e.g., `CreditAccountOpened`).
- **Domain exceptions extend `DomainException`** — all exceptions in `domain.error` extend the base `DomainException` class.
- **Application exceptions extend `RuntimeException`** — all exceptions in `application.error` extend `RuntimeException`.

### DesignRulesFitnessFunctions.java

Verifies design principles:

- **Aggregate exposes only command methods returning `List<CreditAccountEvent>`** — `CreditAccount` does not leak internal state through public fields or getters (except `version()` and `snapshot()`).
- **Only `apply` and `applyAll` mutate aggregate state** — rehydration is separate from command execution.
- **No public fields in production classes** — except records which are inherently transparent.
- **Services use constructor injection** — no `@Autowired` field injection.
- **REST controllers do not access ports directly** — controllers depend only on `application` layer types.

## Mutation Testing — PITest

### Configuration

- Plugin: `info.solidsoft.pitest`
- Target source set: `test` (unit tests only — domain and application)
- Mutators: `ALL` (default PITest mutator set)
- Output: HTML report in `build/reports/pitest/`
- Runs as dependency of `check`

### Thresholds

| Layer | Target classes | Minimum mutation coverage |
|---|---|---|
| Domain | `com.sanmoo.eventsourcing.creditaccount.domain.*` | 100% |
| Application | `com.sanmoo.eventsourcing.creditaccount.application.*` | 80% |

### Exclusions

- `application.command.*` — pure records with no logic.
- `application.result.*` — pure records with no logic.
- `domain.model.*` — value object records with minimal behavior.
- `domain.error.*` — simple exception classes.

These exclusions avoid penalizing mutation coverage for boilerplate types with no testable logic.

### Test gaps

The current `CreditAccountCommandServiceTest` covers only `openCreditAccount`. Reaching 80% mutation coverage on the application layer requires adding unit tests for the remaining commands (assignCreditLimit, changeCreditLimit, authorizePurchase, capturePurchase, releasePurchaseAuthorization, receivePayment). This work is part of the implementation plan.

Domain tests may need additional boundary-condition tests to kill specific mutations (e.g., edge cases in `Money.isGreaterThan`, `Money.isLessThan`).

## Static Code Analysis

### Checkstyle

- Plugin: Gradle built-in `checkstyle`
- Rules: default (Sun conventions embedded in the plugin)
- Scope: `main` source set
- `maxErrors = 0`, `maxWarnings = 0`
- Fails the build on any violation

### PMD

- Plugin: Gradle built-in `pmd`
- Rules: default rule sets (`bestpractices`, `codestyle`, `design`, `errorprone`, `multithreading`, `performance`, `security`)
- Scope: `main` source set
- Console output enabled
- Fails the build on any violation

### SpotBugs

- Plugin: `com.github.spotbugs`
- Rules: default (medium and high priority detections)
- Effort: `max`
- Scope: `main` source set
- Report: HTML + console
- Fails the build on any bug found

### Error Prone

- Plugin: `net.ltgt.errorprone`
- Integration: replaces `javac` during compilation — runs automatically on `compileJava`
- Rules: default (all Error Prone checks)
- Warnings treated as errors
- Must be compatible with Java 25 (verify during implementation)
- Fails the build on any warning

## Quality Gateway — Execution Flow

Running `./gradlew check` executes all checks in sequence:

```
compileJava ──────→ Error Prone (compile-time bugs)
    │
    ▼
checkstyleMain ──→ Checkstyle (style and conventions)
pmdMain ─────────→ PMD (quality, complexity, design)
spotbugsMain ────→ SpotBugs (bytecode-level bugs)
    │
    ▼
test ────────────→ Functional tests (domain + application + integration)
    │
    ▼
qualityTest ─────→ ArchUnit fitness functions (architecture, naming, design)
    │
    ▼
pitest ──────────→ Mutation testing (domain 100%, application 80%)
```

Each step fails the build immediately on violation. Fast failure — no point continuing if an earlier stage is broken.

### Dependency relationships

- `qualityTest` depends on `test` (fitness functions only meaningful if functional tests pass).
- `pitest` depends on `test` (mutation testing requires green tests).
- Error Prone, Checkstyle, PMD, SpotBugs run independently of each other (Gradle handles parallelism).

### Reports

| Tool | Report location |
|---|---|
| Checkstyle | `build/reports/checkstyle/` |
| PMD | `build/reports/pmd/` |
| SpotBugs | `build/reports/spotbugs/` |
| PITest | `build/reports/pitest/` |
| ArchUnit | Standard JUnit output (console + `build/reports/tests/qualityTest/`) |

## Quality Gateway — Pass Criteria

The build is green only if **all** conditions are met:

1. Error Prone: zero warnings
2. Checkstyle: zero violations
3. PMD: zero violations
4. SpotBugs: zero bugs
5. Functional tests: 100% passing
6. ArchUnit fitness functions: all passing
7. Mutation coverage domain ≥ 100%
8. Mutation coverage application ≥ 80%

## Risk Assessment

- **Error Prone + Java 25 compatibility**: Error Prone may lag behind the latest JDK. If incompatible at implementation time, we will pin to the latest supported version and add a comment for future upgrade. If completely incompatible, we will temporarily disable and re-enable when support lands.
- **Default rules may produce noise**: Checkstyle/PMD default rules may flag violations in existing code. Since the project is small and well-structured, cleanup should be minimal. Any rule that conflicts with intentional design choices will be suppressed with explanation comments, not disabled globally.
- **PITest 80% on application layer**: The current application test suite only covers `openCreditAccount`. Additional tests are required. This is expected work, not a risk.
- **Build time impact**: Adding PITest and ArchUnit to `check` increases build time. For this project's size, the impact is acceptable. If build time becomes problematic as the project grows, PITest can be moved to a separate task.
