# Quality Gateway Gap Closure Design

Date: 2026-06-02

## Summary

Close the remaining gaps from the previous quality gateway implementation. The gateway already runs through `./gradlew check`, but three gaps remain: PMD is non-blocking, PITest uses a 72% threshold instead of the intended 80%, and Error Prone warnings cannot currently be made blocking through the plugin DSL.

This design focuses on making PMD blocking and raising mutation coverage to at least 80%. Error Prone remains active, but warning-as-error support is out of scope unless directly supported by the plugin without custom workarounds.

## Goals

- Make PMD a blocking quality gate.
- Calibrate the PMD ruleset so it blocks high-signal issues without noisy style preferences.
- Raise PITest mutation coverage threshold from 72% to 80%.
- Add targeted application-layer tests to reach mutation coverage >= 80%.
- Preserve all currently passing gates: tests, Checkstyle, SpotBugs, ArchUnit, and Error Prone compile-time checks.

## Non-Goals

- Do not split PITest into separate domain/application tasks.
- Do not add custom Gradle tasks to parse Error Prone warnings.
- Do not replace Error Prone, PMD, Checkstyle, SpotBugs, PITest, or ArchUnit.
- Do not add SonarQube/SonarCloud.
- Do not perform broad architectural refactoring.
- Do not force all PMD style preference rules into the initial blocking gate.

## Current Gaps

### PMD is non-blocking

Current build configuration:

```kotlin
pmd {
    isIgnoreFailures = true
}
```

Current PMD output shows many violations that are mostly rule noise for this project, including:

- `UseExplicitTypes`
- `AvoidDuplicateLiterals`
- `MissingSerialVersionUID`
- `AtLeastOneConstructor`
- `ShortMethodName`
- `ControlStatementBraces`

These rules are not all appropriate for the first blocking PMD gate.

### PITest threshold is 72%

Current build configuration:

```kotlin
pitest {
    mutationThreshold = 72
}
```

The design goal is at least 80%. Domain mutation coverage is already at 100%; the gap is primarily in application-service behavior.

### Error Prone warnings are non-blocking

Current build configuration:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        allErrorsAsWarnings = false
        disableWarningsInGeneratedCode = true
    }
}
```

The currently used `net.ltgt.errorprone` plugin version does not expose `allWarningsAsErrors`. Earlier attempts to set it directly caused Gradle script compilation failure. This gap is explicitly out of scope unless the plugin supports it directly during implementation.

## PMD Strategy

PMD becomes blocking after calibrating the ruleset.

### Rule categories

#### Disable as noise for this project

- `UseExplicitTypes`: local type inference is acceptable when obvious.
- `AvoidDuplicateLiterals`: repeated literals in tests/controllers are acceptable unless they hide a real domain concept.
- `MissingSerialVersionUID`: simple runtime exceptions do not need this in the current context.
- `AtLeastOneConstructor`: stateless Spring components do not need explicit constructors.
- `ShortMethodName`: factory methods like `of()` are idiomatic for value objects.
- `ControlStatementBraces`: Checkstyle already covers enough style enforcement; PMD should not duplicate this.
- Rules that only force cosmetic style changes without increasing correctness.

#### Keep as blocking

- High-signal `errorprone` rules.
- Best-practice rules that detect real maintainability or correctness issues.
- Performance rules that detect real inefficiencies.
- Targeted simple fixes such as `UseDiamondOperator` or `ReplaceJavaUtilDate` when they appear.

### Build configuration

Change PMD back to blocking:

```kotlin
pmd {
    isIgnoreFailures = false
}
```

Keep the PMD HTML report enabled.

### Expected result

`./gradlew pmdMain` passes with zero blocking PMD violations.

## Mutation Testing Strategy

Keep the existing single `pitest` task. Do not split thresholds by layer in this iteration.

Raise the configured mutation threshold:

```kotlin
pitest {
    mutationThreshold = 80
}
```

### Test target

Focus on:

```text
src/test/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandServiceTest.java
```

The main production target is:

```text
src/main/java/com/sanmoo/eventsourcing/creditaccount/application/service/CreditAccountCommandService.java
```

### Behaviors to strengthen

Add targeted tests for application-service paths that create surviving mutants:

- Idempotency `Conflict` for more than one command path.
- Invalid replay payload handling.
- `getAccount` for missing account history.
- `getAccount` with richer account state, including authorization data.
- Response data after command execution, including:
  - `creditLimit`
  - `outstandingBalance`
  - `authorizedAmount`
  - `availableLimit`
  - `authorizations`
- Verification that `idempotencyPort.complete()` receives serialized response payload after successful command execution.
- Expected aggregate version differences across command paths.

### Refactoring allowance

If tests alone do not reach 80%, allow small, local refactoring in `CreditAccountCommandService` to improve testability and reduce repeated branching.

Allowed examples:

- Extract repeated idempotency decision handling.
- Extract response data construction helper behavior.
- Make error paths easier to trigger from tests.

Constraints:

- Public behavior must not change.
- REST/API behavior must not change.
- Event formats must not change.
- Refactoring must remain local to the application service and tests.

### Expected result

`./gradlew pitest` passes with mutation score >= 80%.

## Error Prone Decision

Error Prone remains enabled.

No custom workaround is implemented for warnings-as-errors.

Acceptance for this iteration:

- Error Prone compile-time errors still fail the build.
- Warnings may remain non-blocking because direct plugin support is unavailable.
- The limitation remains documented in the build or spec.

## Verification

Run these commands:

```bash
./gradlew pmdMain
./gradlew pitest
./gradlew clean check
```

## Acceptance Criteria

The work is complete when:

1. `pmdMain` passes with `isIgnoreFailures = false`.
2. `pitest` passes with `mutationThreshold = 80`.
3. `clean check` passes.
4. Checkstyle remains green.
5. SpotBugs remains green.
6. ArchUnit `qualityTest` remains green.
7. Functional tests remain green.
8. Error Prone remains active for Java compilation.

## Risks

- PMD rule calibration can become too lax. To avoid this, every disabled rule must be intentionally omitted because it is noisy or style-only for this codebase.
- PITest may require many tests for mutation score gains. If so, prefer targeted behavioral tests before refactoring.
- Raising mutation score by excluding meaningful classes is not acceptable. Exclusions should only cover boilerplate or low-value mutation targets already agreed in the previous design.
