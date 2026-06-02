# Quality Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a comprehensive quality gateway to the project — ArchUnit fitness functions, PITest mutation testing (domain 100%, application 80%), and static analysis (Checkstyle, PMD, SpotBugs, Error Prone) — all wired into `./gradlew check`.

**Architecture:** A single Gradle source set `qualityTest` for ArchUnit fitness functions, separate from the main `test` source set. Three ArchUnit classes (architecture isolation, naming conventions, design rules). Two PITest tasks with different thresholds. Four static analysis tools configured with defaults. All checks are blocking — zero tolerance.

**Tech Stack:** Gradle 9.5.1, Spring Boot 4.0.6, Java 25, PITest 1.19.0 (gradle-pitest-plugin), SpotBugs 6.5.5, Error Prone 5.1.0 (net.ltgt.errorprone), ArchUnit 1.4.2, Checkstyle (built-in), PMD (built-in).

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `build.gradle.kts` | Modify | Plugins, dependencies, source set, all quality tool configurations |
| `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java` | Create | Layer isolation rules |
| `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/NamingConventionFitnessFunctions.java` | Create | Package structure and naming rules |
| `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/DesignRulesFitnessFunctions.java` | Create | Aggregate design, DI, encapsulation rules |
| `src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java` | Modify | Add tests for remaining commands (PITest coverage gap) |
| `src/main/java/com/sanmoo/eventsourcing/creditaccount/` (various) | Modify (minor) | Fix violations found by static analysis tools |

---

### Task 1: Copy current build.gradle.kts as baseline

**Files:**
- No changes yet — capture baseline

- [ ] **Step 1: Backup the current build file**

```bash
cp build.gradle.kts build.gradle.kts.bak
```

- [ ] **Step 2: Verify current build passes**

```bash
./gradlew clean test
```
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 3: Commit baseline**

```bash
git add build.gradle.kts.bak
git commit -m "chore: backup build.gradle.kts before quality gateway changes"
```

---

### Task 2: Add all plugins and dependencies

**Files:**
- Modify: `build.gradle.kts` (plugins block and dependencies block)

- [ ] **Step 1: Replace the plugins block and dependencies block**

Replace the entire `plugins { ... }` block with:

```kotlin
plugins {
    java
    checkstyle
    pmd
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.5.5"
    id("net.ltgt.errorprone") version "5.1.0"
    id("info.solidsoft.pitest") version "1.19.0"
}
```

Replace the entire `dependencies { ... }` block with:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    runtimeOnly("org.postgresql:postgresql")
    errorprone("com.google.errorprone:error_prone_core:2.38.0")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    "qualityTestImplementation"("com.tngtech.archunit:archunit-junit5:1.4.2")
}
```

- [ ] **Step 2: Verify plugins resolve**

```bash
./gradlew tasks --group verification
```
Expected: No plugin resolution errors. You should see tasks like `checkstyleMain`, `pmdMain`, `spotbugsMain` listed under verification group.

- [ ] **Step 3: Run compile to verify Error Prone plugin hooks in**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL. Error Prone may produce warnings but should not fail yet (default is warn, we'll make it strict later).

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add quality gateway plugins and dependencies"
```

---

### Task 3: Configure qualityTest source set

**Files:**
- Modify: `build.gradle.kts` (add source set, task, and wire into `check`)

- [ ] **Step 1: Append source set and test task configuration to build.gradle.kts**

Add this block at the end of `build.gradle.kts`, after the existing `tasks.withType<Test>` block:

```kotlin
// ── qualityTest source set ──────────────────────────────────────────
sourceSets {
    create("qualityTest") {
        java {
            compileClasspath += sourceSets.main.get().output
            runtimeClasspath += sourceSets.main.get().output
        }
    }
}

val qualityTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

val qualityTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.register<Test>("qualityTest") {
    description = "Runs ArchUnit architecture fitness functions"
    group = "verification"
    testClassesDirs = sourceSets["qualityTest"].output.classesDirs
    classpath = sourceSets["qualityTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn("qualityTest")
}
```

- [ ] **Step 2: Create a smoke-test class to verify the source set works**

```bash
mkdir -p src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality
```

Create `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/package-info.java`:

```java
/**
 * Architecture fitness functions using ArchUnit.
 * These tests enforce architectural invariants, naming conventions,
 * and design rules. They are separate from behavioral tests in src/test.
 */
package com.sanmoo.eventsourcing.creditaccount.quality;
```

Create `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/QualityTestConfiguration.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.quality;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;

/**
 * Shared test configuration for all ArchUnit fitness function tests.
 * Imports all classes once at startup to avoid repeated I/O.
 */
public class QualityTestConfiguration {

    protected static final JavaClasses PRODUCTION_CLASSES =
            new ClassFileImporter().importPackages("com.sanmoo.eventsourcing.creditaccount");
}
```

- [ ] **Step 3: Verify the source set compiles and runs (zero tests is OK)**

```bash
./gradlew qualityTest
```
Expected: BUILD SUCCESSFUL. The task should compile `QualityTestConfiguration` and report no tests found (since it's not a test class — no `@Test` methods). If the build fails, check that the dependencies resolve correctly.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts src/qualityTest/
git commit -m "build: add qualityTest source set with ArchUnit dependency"
```

---

### Task 4: Configure Checkstyle (zero tolerance)

**Files:**
- Modify: `build.gradle.kts` (add checkstyle configuration)

- [ ] **Step 1: Add checkstyle configuration to build.gradle.kts**

Append this block before the `qualityTest` source set section (right after the `tasks.withType<Test>` block):

```kotlin
// ── Checkstyle ──────────────────────────────────────────────────────
checkstyle {
    toolVersion = "10.23.0"
    maxErrors = 0
    maxWarnings = 0
    // Uses default Sun conventions (no custom config file yet)
}
```

Note: If `checkstyle { }` DSL isn't recognized without a config file, Gradle will warn but still run with defaults. To explicitly set defaults, create an empty `config/checkstyle/` directory:

```bash
mkdir -p config/checkstyle config/pmd config/spotbugs
```

- [ ] **Step 2: Run checkstyle and see what happens**

```bash
./gradlew checkstyleMain
```
Expected: This MAY fail with violations in existing code. We'll fix them in Task 12. For now, observe the output to know what needs fixing.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts config/
git commit -m "build: configure Checkstyle with zero tolerance"
```

---

### Task 5: Configure PMD (zero tolerance)

**Files:**
- Modify: `build.gradle.kts` (add pmd configuration)

- [ ] **Step 1: Add PMD configuration to build.gradle.kts**

Append this block after the Checkstyle configuration:

```kotlin
// ── PMD ─────────────────────────────────────────────────────────────
pmd {
    toolVersion = "7.12.0"
    isConsoleOutput = true
    ruleSets = listOf() // empty = use default built-in rules
    ruleSetFiles = files() // no custom rules yet
    isIgnoreFailures = false
}

tasks.pmdMain {
    reports {
        html.required = true
        xml.required = false
    }
}
```

- [ ] **Step 2: Run PMD**

```bash
./gradlew pmdMain
```
Expected: May fail with existing violations. Observe output. We'll fix in Task 12.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: configure PMD with zero tolerance"
```

---

### Task 6: Configure SpotBugs (zero tolerance)

**Files:**
- Modify: `build.gradle.kts` (add spotbugs configuration)

- [ ] **Step 1: Add SpotBugs configuration to build.gradle.kts**

Append this block after the PMD configuration:

```kotlin
// ── SpotBugs ────────────────────────────────────────────────────────
spotbugs {
    toolVersion = "4.9.8"
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    ignoreFailures = false
}

tasks.spotbugsMain {
    reports.create("html") { required = true }
    reports.create("xml") { required = false }
}
```

- [ ] **Step 2: Run SpotBugs**

```bash
./gradlew spotbugsMain
```
Expected: May find issues. Observe output. Fix in Task 12.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: configure SpotBugs with zero tolerance"
```

---

### Task 7: Configure Error Prone (all warnings as errors)

**Files:**
- Modify: `build.gradle.kts` (add errorprone configuration)

- [ ] **Step 1: Add Error Prone configuration to build.gradle.kts**

Append this block after the SpotBugs configuration:

```kotlin
// ── Error Prone ─────────────────────────────────────────────────────
import net.ltgt.gradle.errorprone.errorprone

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        // Treat all warnings as errors — build fails on any finding
        allErrorsAsWarnings = false
        disableWarningsInGeneratedCode = true
        // Disable specific checks if they conflict with project conventions
        // (none disabled initially — add here if needed after first run)
    }
}
```

- [ ] **Step 2: Compile with Error Prone**

```bash
./gradlew compileJava --rerun-tasks
```
Expected: May find issues. If the build fails with Error Prone errors, note them for fix in Task 12. If you get compilation errors related to Error Prone itself (not our code), it may be a Java 25 compatibility issue — see Risk notes.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: configure Error Prone with all warnings as errors"
```

---

### Task 8: Configure PITest (domain 100%, application 80%)

**Files:**
- Modify: `build.gradle.kts` (add PITest configuration with two tasks)

- [ ] **Step 1: Add PITest configuration to build.gradle.kts**

Append this block after the Error Prone configuration:

```kotlin
// ── PITest (Mutation Testing) ───────────────────────────────────────
// Two tasks with different thresholds:
//   pitestDomain    → domain layer, 100% mutation coverage required
//   pitestApplication → application layer, 80% mutation coverage required

val excludedClasses = setOf(
    "com.sanmoo.eventsourcing.creditaccount.domain.model.*",
    "com.sanmoo.eventsourcing.creditaccount.domain.error.*",
    "com.sanmoo.eventsourcing.creditaccount.application.command.*",
    "com.sanmoo.eventsourcing.creditaccount.application.result.*",
    "com.sanmoo.eventsourcing.creditaccount.application.error.*"
)

val sharedPitestConfig: com.github.szpak.gradle.pitest.PitestAggregatorExtension.() -> Unit = {
    pitestVersion = "1.19.5"
    junit5PluginVersion = "1.2.1"
    mutators = setOf("ALL")
    outputFormats = setOf("HTML")
    timestampedReports = false
    excludedClasses = excludedClasses
    threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
}

// Domain PITest: 100% mutation coverage
tasks.register<com.github.szpak.gradle.pitest.PitestTask>("pitestDomain") {
    description = "Mutation testing for domain layer (100% required)"
    group = "verification"
    dependsOn(tasks.test)

    targetClasses = setOf("com.sanmoo.eventsourcing.creditaccount.domain.*")
    mutationThreshold = 100
    coverageThreshold = 100

    sharedPitestConfig(this as com.github.szpak.gradle.pitest.PitestAggregatorExtension)
}

// Application PITest: 80% mutation coverage
tasks.register<com.github.szpak.gradle.pitest.PitestTask>("pitestApplication") {
    description = "Mutation testing for application layer (80% required)"
    group = "verification"
    dependsOn(tasks.test)

    targetClasses = setOf("com.sanmoo.eventsourcing.creditaccount.application.*")
    mutationThreshold = 80
    coverageThreshold = 80

    sharedPitestConfig(this as com.github.szpak.gradle.pitest.PitestAggregatorExtension)
}

// Disable the default pitest task — we use our own
tasks.named("pitest") {
    enabled = false
}

tasks.check {
    dependsOn("pitestDomain", "pitestApplication")
}
```

- [ ] **Step 2: Clean old artifacts and verify PITest tasks register**

```bash
./gradlew clean tasks --group verification
```
Expected: `pitestDomain` and `pitestApplication` tasks should be listed under verification.

- [ ] **Step 3: Run domain PITest (should pass or be close)**

```bash
./gradlew pitestDomain
```
Expected: May pass or fail depending on exact coverage. If it fails below 100%, note which mutations survived — we'll add tests in Task 11 if needed.

- [ ] **Step 4: Run application PITest (will likely fail — coverage gaps)**

```bash
./gradlew pitestApplication
```
Expected: Will almost certainly fail below 80% because `CreditAccountCommandServiceTest` only covers `openCreditAccount`. Note the surviving mutations — we'll add tests in Task 11.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts
git commit -m "build: configure PITest with domain 100% and application 80% thresholds"
```

---

### Task 9: Write ArchitectureFitnessFunctions (layer isolation)

**Files:**
- Create: `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java`

- [ ] **Step 1: Write the test class**

Create `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/ArchitectureFitnessFunctions.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.quality;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.sanmoo.eventsourcing.creditaccount")
public class ArchitectureFitnessFunctions {

    /**
     * The domain layer must not depend on any external framework or adapter.
     * Permitted dependencies: java.*, and the domain package itself.
     */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_external_frameworks = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.domain.."
            );

    /**
     * The application layer must not depend on adapters (inbound or outbound).
     * Permitted dependencies: java.*, domain, and application itself.
     */
    @ArchTest
    static final ArchRule application_must_not_depend_on_adapters = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.domain..",
                    "com.sanmoo.eventsourcing.creditaccount.application..",
                    "tools.jackson.."  // Jackson is used in idempotency serialization within application
            );

    /**
     * Inbound adapters must not depend on outbound adapters.
     */
    @ArchTest
    static final ArchRule inbound_adapters_must_not_depend_on_outbound_adapters = classes()
            .that().resideInAPackage("..adapter.in..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.adapter.in..",
                    "com.sanmoo.eventsourcing.creditaccount.application..",
                    "com.sanmoo.eventsourcing.creditaccount.domain..",
                    "org.springframework..",
                    "tools.jackson..",
                    "jakarta.."
            );

    /**
     * Outbound adapters must not depend on inbound adapters.
     */
    @ArchTest
    static final ArchRule outbound_adapters_must_not_depend_on_inbound_adapters = classes()
            .that().resideInAPackage("..adapter.out..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.adapter.out..",
                    "com.sanmoo.eventsourcing.creditaccount.application..",
                    "com.sanmoo.eventsourcing.creditaccount.domain..",
                    "org.springframework..",
                    "tools.jackson.."
            );

    /**
     * Application port types must be interfaces (except for data-transfer records).
     */
    @ArchTest
    static final ArchRule ports_must_be_interfaces_or_records = classes()
            .that().resideInAPackage("..application.port..")
            .and().areNotRecords()
            .should().beInterfaces()
            .because("application ports should be interfaces; data records are allowed as records");

    /**
     * Domain events must be Java records.
     */
    @ArchTest
    static final ArchRule domain_events_must_be_records = classes()
            .that().resideInAPackage("..domain.event..")
            .should().beRecords()
            .because("domain events are immutable facts and should be records");

    /**
     * Domain model value objects must be Java records.
     */
    @ArchTest
    static final ArchRule domain_model_classes_must_be_records = classes()
            .that().resideInAPackage("..domain.model..")
            .should().beRecords()
            .because("domain value objects are immutable and should be records");
}
```

- [ ] **Step 2: Run the architecture fitness functions**

```bash
./gradlew qualityTest
```
Expected: All rules should pass since the existing code already follows this structure. If any rule fails, investigate — it might reveal an actual architectural violation that needs fixing.

- [ ] **Step 3: Commit**

```bash
git add src/qualityTest/
git commit -m "test: add ArchUnit layer isolation fitness functions"
```

---

### Task 10: Write NamingConventionFitnessFunctions

**Files:**
- Create: `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/NamingConventionFitnessFunctions.java`

- [ ] **Step 1: Write the test class**

Create `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/NamingConventionFitnessFunctions.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.quality;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.sanmoo.eventsourcing.creditaccount")
public class NamingConventionFitnessFunctions {

    /**
     * Command classes must be records and end with "Command".
     */
    @ArchTest
    static final ArchRule commands_must_be_records = classes()
            .that().resideInAPackage("..application.command..")
            .should().beRecords()
            .because("commands are immutable data carriers and should be records");

    @ArchTest
    static final ArchRule commands_must_have_command_suffix = classes()
            .that().resideInAPackage("..application.command..")
            .should().haveSimpleNameEndingWith("Command")
            .because("all command classes should follow the *Command naming convention");

    /**
     * Domain exceptions must extend DomainException.
     */
    @ArchTest
    static final ArchRule domain_exceptions_must_extend_DomainException = classes()
            .that().resideInAPackage("..domain.error..")
            .and().areAssignableTo(RuntimeException.class)
            .should().beAssignableTo(
                    com.sanmoo.eventsourcing.creditaccount.domain.error.DomainException.class
            )
            .because("all domain exceptions must extend the base DomainException");

    /**
     * Application exceptions must extend RuntimeException.
     */
    @ArchTest
    static final ArchRule application_exceptions_must_extend_RuntimeException = classes()
            .that().resideInAPackage("..application.error..")
            .should().beAssignableTo(RuntimeException.class)
            .because("application errors should extend RuntimeException");

    /**
     * No classes should reside directly in the root creditaccount package
     * (except the application entry point).
     */
    @ArchTest
    static final ArchRule root_package_should_only_contain_application_class = classes()
            .that().resideInAPackage("com.sanmoo.eventsourcing.creditaccount")
            .should().haveSimpleName("CreditAccountApplication")
            .because("only the Spring Boot application entry point should be in the root package");
}
```

- [ ] **Step 2: Run the naming convention functions**

```bash
./gradlew qualityTest
```
Expected: All rules should pass. If the `domain_exceptions_must_extend_DomainException` fails, check that all classes in `domain.error` extend `DomainException`.

- [ ] **Step 3: Commit**

```bash
git add src/qualityTest/
git commit -m "test: add ArchUnit naming convention fitness functions"
```

---

### Task 11: Write DesignRulesFitnessFunctions

**Files:**
- Create: `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/DesignRulesFitnessFunctions.java`

- [ ] **Step 1: Write the test class**

Create `src/qualityTest/java/com/sanmoo/eventsourcing/creditaccount/quality/DesignRulesFitnessFunctions.java`:

```java
package com.sanmoo.eventsourcing.creditaccount.quality;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.sanmoo.eventsourcing.creditaccount")
public class DesignRulesFitnessFunctions {

    /**
     * No classes should use field injection (@Autowired on fields).
     * Constructor injection is preferred for testability and clarity.
     */
    @ArchTest
    static final ArchRule no_field_injection = noClasses()
            .should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
            .orShould().containAnyFieldsThat(
                    field -> field.isAnnotatedWith(
                            org.springframework.beans.factory.annotation.Autowired.class
                    )
            )
            .because("constructor injection is preferred over @Autowired field injection");

    /**
     * REST controllers must not directly access application ports.
     * They should only depend on application services.
     */
    @ArchTest
    static final ArchRule controllers_must_not_access_ports = classes()
            .that().resideInAPackage("..adapter.in.rest..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "com.sanmoo.eventsourcing.creditaccount.adapter.in.rest..",
                    "com.sanmoo.eventsourcing.creditaccount.application.command..",
                    "com.sanmoo.eventsourcing.creditaccount.application.result..",
                    "com.sanmoo.eventsourcing.creditaccount.application.service..",
                    "com.sanmoo.eventsourcing.creditaccount.application.error..",
                    "org.springframework..",
                    "tools.jackson..",
                    "jakarta.."
            )
            .because("controllers should depend on the application layer, not directly on ports");

    /**
     * No production class (excluding records) should have non-private fields.
     * Records are excluded because their fields are inherently part of their public API.
     */
    @ArchTest
    static final ArchRule no_public_fields_in_non_record_classes = classes()
            .that().areNotRecords()
            .should().containOnlyPrivateFields()
            .because("encapsulation: non-record classes should not expose fields directly");
}
```

- [ ] **Step 2: Run the design rules functions**

```bash
./gradlew qualityTest
```
Expected: All rules should pass. If `no_public_fields_in_non_record_classes` fails, check for public constants (like `public static final` fields) which are acceptable — we may need to adjust the rule to allow `static final` fields.

- [ ] **Step 3: Commit**

```bash
git add src/qualityTest/
git commit -m "test: add ArchUnit design rules fitness functions"
```

---

### Task 12: Fill application command service test gaps

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java`

The current test file only covers `openCreditAccount` and its idempotency replay. We need tests for all other commands to reach 80% mutation coverage on the application layer.

- [ ] **Step 1: Read current test file to understand existing patterns**

```bash
cat src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java
```

The existing pattern uses mock ports (`EventStorePort`, `IdempotencyPort`) and asserts on `appendEvents` calls and idempotency behavior.

- [ ] **Step 2: Write failing tests for remaining commands (they'll fail until we verify)**

Add these test methods to the existing `CreditAccountCommandServiceTest` class, inside the class body, after the existing tests.

Note: `EventEnvelope` is a 7-field record: `(UUID eventId, String aggregateType, String aggregateId, long aggregateVersion, CreditAccountEvent event, Instant occurredAt, Map<String, String> metadata)`. Use a helper method to reduce boilerplate.

Add this private helper method to the test class:

```java
private EventEnvelope envelope(long version, CreditAccountEvent event, String aggregateId) {
    return new EventEnvelope(
            UUID.randomUUID(), "CreditAccount", aggregateId, version, event,
            Instant.now(), Map.of()
    );
}
```

Then add these test methods:

```java
@Test
void assignCreditLimitAppendsEventAtExpectedVersion() throws Exception {
    String aggregateId = UUID.randomUUID().toString();
    CreditAccountId accountId = CreditAccountId.of(UUID.fromString(aggregateId));
    AssignCreditLimitCommand command = new AssignCreditLimitCommand("key-1", accountId, Money.of("200.00"));

    when(idempotencyPort.start(eq("key-1"), eq("AssignCreditLimit"), any(), any()))
            .thenReturn(new IdempotencyDecision.Started("key-1"));
    when(eventStore.loadEvents(any(), any()))
            .thenReturn(List.of(
                    envelope(0L, new CreditAccountOpened(accountId, Instant.now()), aggregateId)
            ));
    when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
            .thenReturn(new AppendResult(2L));

    CommandResult result = service.assignCreditLimit(command);

    verify(eventStore).appendEvents(
            eq("CreditAccount"),
            eq(aggregateId),
            eq(1L),
            argThat(events -> events.size() == 1 && events.get(0) instanceof CreditLimitAssigned),
            anyMap()
    );
    assertThat(result.aggregateVersion()).isEqualTo(2L);
}

@Test
void authorizePurchaseAppendsEventAtExpectedVersion() throws Exception {
    String aggregateId = UUID.randomUUID().toString();
    CreditAccountId accountId = CreditAccountId.of(UUID.fromString(aggregateId));
    AuthorizationId authorizationId = AuthorizationId.newId();
    AuthorizePurchaseCommand command = new AuthorizePurchaseCommand(
            "key-2", accountId, authorizationId, Money.of("50.00"), "Book Store"
    );

    when(idempotencyPort.start(eq("key-2"), eq("AuthorizePurchase"), any(), any()))
            .thenReturn(new IdempotencyDecision.Started("key-2"));
    when(eventStore.loadEvents(any(), any()))
            .thenReturn(List.of(
                    envelope(0L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                    envelope(1L, new CreditLimitAssigned(accountId, Money.of("500.00"), Instant.now()), aggregateId)
            ));
    when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
            .thenReturn(new AppendResult(3L));

    CommandResult result = service.authorizePurchase(command);

    verify(eventStore).appendEvents(
            eq("CreditAccount"),
            eq(aggregateId),
            eq(2L),
            argThat(events -> events.size() == 1 && events.get(0) instanceof PurchaseAuthorized),
            anyMap()
    );
    assertThat(result.aggregateVersion()).isEqualTo(3L);
}

@Test
void capturePurchaseAppendsEventAtExpectedVersion() throws Exception {
    String aggregateId = UUID.randomUUID().toString();
    CreditAccountId accountId = CreditAccountId.of(UUID.fromString(aggregateId));
    AuthorizationId authorizationId = AuthorizationId.newId();
    CapturePurchaseCommand command = new CapturePurchaseCommand("key-3", accountId, authorizationId);

    when(idempotencyPort.start(eq("key-3"), eq("CapturePurchase"), any(), any()))
            .thenReturn(new IdempotencyDecision.Started("key-3"));
    when(eventStore.loadEvents(any(), any()))
            .thenReturn(List.of(
                    envelope(0L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                    envelope(1L, new CreditLimitAssigned(accountId, Money.of("500.00"), Instant.now()), aggregateId),
                    envelope(2L, new PurchaseAuthorized(accountId, authorizationId, Money.of("50.00"), "Store", Instant.now()), aggregateId)
            ));
    when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
            .thenReturn(new AppendResult(4L));

    CommandResult result = service.capturePurchase(command);

    verify(eventStore).appendEvents(
            eq("CreditAccount"),
            eq(aggregateId),
            eq(3L),
            argThat(events -> events.size() == 1 && events.get(0) instanceof PurchaseCaptured),
            anyMap()
    );
    assertThat(result.aggregateVersion()).isEqualTo(4L);
}

@Test
void releasePurchaseAuthorizationAppendsEventAtExpectedVersion() throws Exception {
    String aggregateId = UUID.randomUUID().toString();
    CreditAccountId accountId = CreditAccountId.of(UUID.fromString(aggregateId));
    AuthorizationId authorizationId = AuthorizationId.newId();
    ReleasePurchaseAuthorizationCommand command = new ReleasePurchaseAuthorizationCommand("key-4", accountId, authorizationId);

    when(idempotencyPort.start(eq("key-4"), eq("ReleasePurchaseAuthorization"), any(), any()))
            .thenReturn(new IdempotencyDecision.Started("key-4"));
    when(eventStore.loadEvents(any(), any()))
            .thenReturn(List.of(
                    envelope(0L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                    envelope(1L, new CreditLimitAssigned(accountId, Money.of("500.00"), Instant.now()), aggregateId),
                    envelope(2L, new PurchaseAuthorized(accountId, authorizationId, Money.of("50.00"), "Store", Instant.now()), aggregateId)
            ));
    when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
            .thenReturn(new AppendResult(4L));

    CommandResult result = service.releasePurchaseAuthorization(command);

    verify(eventStore).appendEvents(
            eq("CreditAccount"),
            eq(aggregateId),
            eq(3L),
            argThat(events -> events.size() == 1 && events.get(0) instanceof PurchaseAuthorizationReleased),
            anyMap()
    );
    assertThat(result.aggregateVersion()).isEqualTo(4L);
}

@Test
void receivePaymentAppendsEventAtExpectedVersion() throws Exception {
    String aggregateId = UUID.randomUUID().toString();
    CreditAccountId accountId = CreditAccountId.of(UUID.fromString(aggregateId));
    AuthorizationId authorizationId = AuthorizationId.newId();
    ReceivePaymentCommand command = new ReceivePaymentCommand("key-5", accountId, Money.of("25.00"));

    when(idempotencyPort.start(eq("key-5"), eq("ReceivePayment"), any(), any()))
            .thenReturn(new IdempotencyDecision.Started("key-5"));
    when(eventStore.loadEvents(any(), any()))
            .thenReturn(List.of(
                    envelope(0L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                    envelope(1L, new CreditLimitAssigned(accountId, Money.of("500.00"), Instant.now()), aggregateId),
                    envelope(2L, new PurchaseAuthorized(accountId, authorizationId, Money.of("50.00"), "Store", Instant.now()), aggregateId),
                    envelope(3L, new PurchaseCaptured(accountId, authorizationId, Money.of("50.00"), Instant.now()), aggregateId)
            ));
    when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
            .thenReturn(new AppendResult(5L));

    CommandResult result = service.receivePayment(command);

    verify(eventStore).appendEvents(
            eq("CreditAccount"),
            eq(aggregateId),
            eq(4L),
            argThat(events -> events.size() == 1 && events.get(0) instanceof PaymentReceived),
            anyMap()
    );
    assertThat(result.aggregateVersion()).isEqualTo(5L);
}

@Test
void changeCreditLimitAppendsEventAtExpectedVersion() throws Exception {
    String aggregateId = UUID.randomUUID().toString();
    CreditAccountId accountId = CreditAccountId.of(UUID.fromString(aggregateId));
    ChangeCreditLimitCommand command = new ChangeCreditLimitCommand("key-6", accountId, Money.of("300.00"));

    when(idempotencyPort.start(eq("key-6"), eq("ChangeCreditLimit"), any(), any()))
            .thenReturn(new IdempotencyDecision.Started("key-6"));
    when(eventStore.loadEvents(any(), any()))
            .thenReturn(List.of(
                    envelope(0L, new CreditAccountOpened(accountId, Instant.now()), aggregateId),
                    envelope(1L, new CreditLimitAssigned(accountId, Money.of("200.00"), Instant.now()), aggregateId)
            ));
    when(eventStore.appendEvents(any(), any(), anyLong(), anyList(), anyMap()))
            .thenReturn(new AppendResult(3L));

    CommandResult result = service.changeCreditLimit(command);

    verify(eventStore).appendEvents(
            eq("CreditAccount"),
            eq(aggregateId),
            eq(2L),
            argThat(events -> events.size() == 1 && events.get(0) instanceof CreditLimitChanged),
            anyMap()
    );
    assertThat(result.aggregateVersion()).isEqualTo(3L);
}

@Test
void commandReplayReturnsPreviousResultWithoutAppending() throws Exception {
    String aggregateId = UUID.randomUUID().toString();
    CreditAccountId accountId = CreditAccountId.of(UUID.fromString(aggregateId));
    AssignCreditLimitCommand command = new AssignCreditLimitCommand("key-7", accountId, Money.of("200.00"));

    Map<String, Object> storedData = Map.of("creditAccountId", aggregateId, "limitAssigned", true);
    CommandResult previousResult = new CommandResult(aggregateId, 2L, storedData);
    String storedPayload = objectMapper.writeValueAsString(previousResult);

    when(idempotencyPort.start(eq("key-7"), eq("AssignCreditLimit"), any(), any()))
            .thenReturn(new IdempotencyDecision.Replay(storedPayload));

    CommandResult result = service.assignCreditLimit(command);

    verify(eventStore, never()).loadEvents(any(), any());
    verify(eventStore, never()).appendEvents(any(), any(), anyLong(), anyList(), anyMap());
    assertThat(result.aggregateId()).isEqualTo(aggregateId);
    assertThat(result.aggregateVersion()).isEqualTo(2L);
}
```

- [ ] **Step 3: Add missing imports at the top of the test file**

Add these imports to the existing `CreditAccountCommandServiceTest`:

```java
import com.sanmoo.eventsourcing.creditaccount.application.command.AssignCreditLimitCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.AuthorizePurchaseCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.CapturePurchaseCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.ChangeCreditLimitCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.ReceivePaymentCommand;
import com.sanmoo.eventsourcing.creditaccount.application.command.ReleasePurchaseAuthorizationCommand;
import com.sanmoo.eventsourcing.creditaccount.domain.model.AuthorizationId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import com.sanmoo.eventsourcing.creditaccount.domain.model.Money;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditAccountOpened;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitAssigned;
import com.sanmoo.eventsourcing.creditaccount.domain.event.CreditLimitChanged;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PaymentReceived;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorized;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseAuthorizationReleased;
import com.sanmoo.eventsourcing.creditaccount.domain.event.PurchaseCaptured;
import com.sanmoo.eventsourcing.creditaccount.application.port.EventEnvelope;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
```

- [ ] **Step 4: Run the new tests to verify they pass**

```bash
./gradlew test --tests "com.sanmoo.eventsourcing.creditaccount.application.service.CreditAccountCommandServiceTest"
```
Expected: All tests should pass (including the 2 existing ones). If tests fail, read the error — it may be a type mismatch between test data and actual service method signatures. Check the service class for exact method signatures.

- [ ] **Step 5: Commit**

```bash
git add src/test/
git commit -m "test: add application command service tests for all commands"
```

---

### Task 13: Run full `./gradlew check` and fix violations

**Files:**
- Various files in `src/main/` (fix violations found by static analysis)
- Possibly `build.gradle.kts` (if we need to suppress a specific unavoidable rule)

- [ ] **Step 1: Run the full check**

```bash
./gradlew clean check 2>&1 | tee build-output.log
```
Expected: The build will likely fail on some static analysis tool. This is expected — the project was not previously analyzed by Checkstyle, PMD, SpotBugs, or Error Prone.

- [ ] **Step 2: Triage failures**

Look at the output to identify which tools failed and why. Common categories:

1. **Checkstyle violations** — likely about formatting (line length, whitespace, Javadoc). These are style fixes.
2. **PMD violations** — likely about naming, complexity, unused imports. These are quality fixes.
3. **SpotBugs findings** — least likely for this well-structured code, but possible.
4. **Error Prone errors** — compilation-level issues. May reveal actual bugs.
5. **PITest** — likely failing on application coverage below 80%.
6. **ArchUnit** — likely passing since we just wrote tests against existing code.

- [ ] **Step 3: Fix violations by category**

For each failing tool, fix violations in the source code. Priority order:

1. **Error Prone** — these are potential bugs. Fix first.
2. **PMD/Checkstyle** — style and quality. Fix in batch.
3. **SpotBugs** — bytecode bugs. May require more investigation.

For Checkstyle/PMD that flag intentional design choices (e.g., "too many methods" on `CreditAccount`), suppress the specific rule with a comment rather than changing the design:

```java
@SuppressWarnings("PMD.TooManyMethods") // aggregate pattern requires multiple command methods
public final class CreditAccount {
```

- [ ] **Step 4: Re-run check after fixes**

```bash
./gradlew clean check
```
Expected: Repeat fix-and-run until all tools pass. If PITest application is the only failing check, ensure Task 11 tests are complete and correct.

- [ ] **Step 5: Commit fixes**

```bash
git add -A
git commit -m "fix: resolve static analysis violations across the codebase"
```

---

### Task 14: Final verification

**Files:**
- No new changes — verification only

- [ ] **Step 1: Run full clean check**

```bash
./gradlew clean check
```
Expected: BUILD SUCCESSFUL. All 9 gates must pass:
1. ✅ Error Prone: zero warnings
2. ✅ Checkstyle: zero violations
3. ✅ PMD: zero violations
4. ✅ SpotBugs: zero bugs
5. ✅ Functional tests: all passing
6. ✅ ArchUnit fitness functions: all passing
7. ✅ PITest domain: ≥ 100% mutation coverage
8. ✅ PITest application: ≥ 80% mutation coverage

- [ ] **Step 2: Inspect reports**

```bash
ls -la build/reports/
```
Verify that all report directories exist and contain output.

- [ ] **Step 3: Commit final clean state**

```bash
git status
git diff --stat
```
If no outstanding changes, mark this task done. If changes remain from fix-up iterations, commit them:

```bash
git add -A
git commit -m "chore: final cleanup after quality gateway verification"
```

- [ ] **Step 4: Review the commit log**

```bash
git log --oneline -15
```
Verify the commit history tells a clear story of the quality gateway construction.

---

### Risk Notes

**Error Prone + Java 25 compatibility:** If Error Prone 2.38.0 does not support Java 25, try a newer release. Search for the latest:
```bash
# Check latest Error Prone version
curl -s https://repo1.maven.org/maven2/com/google/errorprone/error_prone_core/maven-metadata.xml | grep '<release>'
```

If no Java 25-compatible version exists, temporarily set:
```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone.isEnabled = false
}
```
And add a TODO comment to re-enable when support lands. This is an acceptable fallback per the design.

**SpotBugs Effort/Confidence enums:** If `com.github.spotbugs.snom.Effort` or `Confidence` don't compile in SpotBugs 6.5.5, use Gradle property-style configuration instead:
```kotlin
spotbugs {
    toolVersion = "4.9.8"
    tasks.spotbugsMain {
        reports.create("html") { required = true }
    }
}
// Configure via task properties:
tasks.spotbugsMain {
    effort = "max"
    reportLevel = "medium"
}
```

**PITest task type import:** If `com.github.szpak.gradle.pitest.PitestTask` doesn't resolve (the class may be `PitestTask` in a different package or named differently), use this fallback: remove the separate `pitestDomain` and `pitestApplication` tasks, use the default `pitest` task with a combined threshold, and verify domain/application coverage manually in reports.

```kotlin
pitest {
    pitestVersion = "1.19.5"
    junit5PluginVersion = "1.2.1"
    targetClasses = setOf("com.sanmoo.eventsourcing.creditaccount.domain.*", "com.sanmoo.eventsourcing.creditaccount.application.*")
    excludedClasses = excludedClasses
    mutators = setOf("ALL")
    mutationThreshold = 80  // combined minimum
    outputFormats = setOf("HTML")
}
tasks.check { dependsOn("pitest") }
```

**Checkstyle default rules:** The default Checkstyle rules (Sun style) are more restrictive than Google style. If they flag too many minor issues (e.g., "Missing a Javadoc comment" on self-documenting methods), consider switching to the Google style:
```kotlin
checkstyle {
    toolVersion = "10.23.0"
    maxErrors = 0
    maxWarnings = 0
    config = resources.text.fromFile("config/checkstyle/google_checks.xml")
}
```
Download the config:
```bash
curl -o config/checkstyle/google_checks.xml \
  https://raw.githubusercontent.com/checkstyle/checkstyle/master/src/main/resources/google_checks.xml
```
