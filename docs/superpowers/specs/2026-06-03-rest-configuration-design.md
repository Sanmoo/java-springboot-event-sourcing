# RestConfiguration Bean Wiring Design

Date: 2026-06-03

## Problem

`RestConfiguration` currently declares Spring beans for `CreditAccountUseCaseSupport` and each credit-account use case. The question is whether that configuration class is necessary, or whether annotating the use case classes with Spring stereotypes is sufficient.

## Decision

Remove `RestConfiguration` and let Spring discover the use case classes directly.

Because the project now allows Spring annotations in the `core` layer, each use case and the shared support class will be annotated with `@Service`. This makes `RestConfiguration` unnecessary for dependency injection.

## Architecture

The final wiring will be:

1. `CreditAccountController` receives use cases through constructor injection.
2. Spring creates each `*UseCase` as a service bean.
3. Spring injects `CreditAccountUseCaseSupport` into each use case constructor.
4. Spring creates `CreditAccountUseCaseSupport` as a service bean.
5. Spring injects `EventStorePort`, `IdempotencyPort`, and `ObjectMapper` into `CreditAccountUseCaseSupport`.
6. Existing adapter beans continue providing the port implementations.

## Components to Change

Add `@Service` to:

- `CreditAccountUseCaseSupport`
- `OpenCreditAccountUseCase`
- `AssignCreditLimitUseCase`
- `ChangeCreditLimitUseCase`
- `AuthorizePurchaseUseCase`
- `CapturePurchaseUseCase`
- `ReleasePurchaseAuthorizationUseCase`
- `ReceivePaymentUseCase`
- `GetCreditAccountUseCase`

Remove:

- `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestConfiguration.java`

## Rationale

`RestConfiguration` was useful while keeping `core` free from Spring dependencies, because it assembled core objects from the framework edge. Once Spring annotations are allowed in `core`, the configuration becomes boilerplate.

Using `@Service` keeps dependency wiring close to the classes being wired and reduces the maintenance cost when new use cases are added.

## Consequences

Positive:

- Less manual bean configuration.
- Clear Spring-managed application services.
- Adding a new use case requires annotating the class, not editing a separate configuration file.

Trade-off:

- The `core` use case package now depends on Spring's stereotype annotation.

Accepted because the user confirmed that Spring annotations are allowed in `core`.

## Validation Plan

Verify Spring wiring and compilation with:

```bash
./gradlew compileJava
./gradlew test --tests '*CreditAccountEventSourcingApplicationTests'
```

If Docker/Testcontainers is available, also run the REST integration test:

```bash
./gradlew test --tests '*CreditAccountControllerIT'
```

If Docker is unavailable, record that limitation and rely on compilation plus non-container context tests where possible.
