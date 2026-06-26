# Graph Report - .  (2026-06-26)

## Corpus Check
- 205 files · ~88,148 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 786 nodes · 2492 edges · 51 communities (31 shown, 20 thin omitted)
- Extraction: 72% EXTRACTED · 28% INFERRED · 0% AMBIGUOUS · INFERRED: 704 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Projection & Outbox Delivery|Projection & Outbox Delivery]]
- [[_COMMUNITY_Acceptance Tests & Boot|Acceptance Tests & Boot]]
- [[_COMMUNITY_Use Case & Domain Tests|Use Case & Domain Tests]]
- [[_COMMUNITY_Domain Identifiers & Value Objects|Domain Identifiers & Value Objects]]
- [[_COMMUNITY_Credit Account Aggregate|Credit Account Aggregate]]
- [[_COMMUNITY_REST Request DTOs|REST Request DTOs]]
- [[_COMMUNITY_Account Listing & Pagination|Account Listing & Pagination]]
- [[_COMMUNITY_Database Schema|Database Schema]]
- [[_COMMUNITY_Account Query Use Case|Account Query Use Case]]
- [[_COMMUNITY_Event Sourcing Design Docs|Event Sourcing Design Docs]]
- [[_COMMUNITY_Async Projection Design|Async Projection Design]]
- [[_COMMUNITY_Authorization Exceptions|Authorization Exceptions]]
- [[_COMMUNITY_Projection Tests|Projection Tests]]
- [[_COMMUNITY_REST Exception Handling|REST Exception Handling]]
- [[_COMMUNITY_Use Case Support|Use Case Support]]
- [[_COMMUNITY_Architecture Plans|Architecture Plans]]
- [[_COMMUNITY_Authorize & Payment REST DTOs|Authorize & Payment REST DTOs]]
- [[_COMMUNITY_Quality Gateway|Quality Gateway]]
- [[_COMMUNITY_Async Projection & Infrastructure|Async Projection & Infrastructure]]
- [[_COMMUNITY_Projection Gating|Projection Gating]]
- [[_COMMUNITY_Aggregate State Transitions|Aggregate State Transitions]]
- [[_COMMUNITY_Capture Purchase Use Case|Capture Purchase Use Case]]
- [[_COMMUNITY_Projection Not Ready Handling|Projection Not Ready Handling]]
- [[_COMMUNITY_Conflict Exceptions|Conflict Exceptions]]
- [[_COMMUNITY_Shell API Scripts|Shell API Scripts]]
- [[_COMMUNITY_Acceptance Testing Design|Acceptance Testing Design]]
- [[_COMMUNITY_DTO Mapping|DTO Mapping]]
- [[_COMMUNITY_REST Response DTOs|REST Response DTOs]]
- [[_COMMUNITY_Testcontainers Config|Testcontainers Config]]
- [[_COMMUNITY_Idempotency DB Design|Idempotency DB Design]]
- [[_COMMUNITY_Idempotency Mechanism|Idempotency Mechanism]]
- [[_COMMUNITY_PostgreSQL Infrastructure|PostgreSQL Infrastructure]]
- [[_COMMUNITY_Authorize Purchase Use Case|Authorize Purchase Use Case]]
- [[_COMMUNITY_Change Credit Limit Use Case|Change Credit Limit Use Case]]
- [[_COMMUNITY_Open Credit Account Use Case|Open Credit Account Use Case]]
- [[_COMMUNITY_Receive Payment Use Case|Receive Payment Use Case]]
- [[_COMMUNITY_Release Authorization Use Case|Release Authorization Use Case]]
- [[_COMMUNITY_Account Not Found Handling|Account Not Found Handling]]
- [[_COMMUNITY_Postgres Test Image|Postgres Test Image]]
- [[_COMMUNITY_Authorization Not Found Exception|Authorization Not Found Exception]]
- [[_COMMUNITY_Credit Limit Already Assigned Exception|Credit Limit Already Assigned Exception]]
- [[_COMMUNITY_Invalid Credit Limit Exception|Invalid Credit Limit Exception]]
- [[_COMMUNITY_Payment Exceeds Balance Exception|Payment Exceeds Balance Exception]]
- [[_COMMUNITY_Acceptance Spring Config|Acceptance Spring Config]]
- [[_COMMUNITY_Cucumber Acceptance Test|Cucumber Acceptance Test]]
- [[_COMMUNITY_Architecture Fitness Functions|Architecture Fitness Functions]]
- [[_COMMUNITY_Design Rules Fitness Functions|Design Rules Fitness Functions]]
- [[_COMMUNITY_Naming Convention Fitness Functions|Naming Convention Fitness Functions]]

## God Nodes (most connected - your core abstractions)
1. `CreditAccountOpened` - 46 edges
2. `CreditAccountId` - 46 edges
3. `OutboxEvent` - 38 edges
4. `AuthorizationId` - 36 edges
5. `ProjectionProperties` - 35 edges
6. `Money` - 34 edges
7. `CreditLimitAssigned` - 30 edges
8. `CreditAccountEvent` - 29 edges
9. `ProjectionWorker` - 25 edges
10. `CreditAccountTest` - 25 edges

## Surprising Connections (you probably didn't know these)
- `Versioned Event Types` --conceptually_related_to--> `Event Sourcing`  [INFERRED]
  docs/superpowers/plans/2026-06-09-versioned-event-types-v1.md → README.md
- `Ports and Adapters Architecture` --semantically_similar_to--> `Clean Architecture Use Cases`  [INFERRED] [semantically similar]
  README.md → docs/superpowers/plans/2026-06-02-core-usecases-clean-architecture.md
- `Docker Compose Local PostgreSQL` --references--> `Asynchronous Read Model Projection`  [INFERRED]
  docker-compose.yml → docs/superpowers/plans/2026-06-10-async-read-model-projection.md
- `Plans 2026 06 01 Credit Account Event Sourcing Implementation` --references--> `Event Sourcing`  [INFERRED]
  docs/superpowers/plans/2026-06-01-credit-account-event-sourcing-implementation.md → README.md
- `Plans 2026 06 01 Credit Account Event Sourcing Implementation` --references--> `Idempotent Commands`  [INFERRED]
  docs/superpowers/plans/2026-06-01-credit-account-event-sourcing-implementation.md → README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Event Sourcing Architecture Decisions** — concept_event_sourcing, concept_outbox_pattern, concept_async_projection, concept_cqrs, concept_idempotent_commands [INFERRED 0.85]
- **Quality Gateway Tools** — concept_quality_gateway, concept_archunit, concept_pitest [INFERRED 0.85]
- **Idempotency Mechanism Evolution** — concept_idempotent_commands, concept_advisory_locks, concept_transactional_replay [INFERRED 0.80]
- **Quality Gateway Toolchain** — concept_quality_gateway, concept_archunit, concept_pitest, concept_checkstyle, concept_pmd, concept_spotbugs, concept_error_prone [INFERRED 0.85]
- **Outbox Projection Pipeline** — concept_outbox, concept_outbox_events, concept_outbox_deliveries, concept_projection_checkpoints, concept_projection_worker, concept_creditaccountsummaryprojector, concept_read_model [INFERRED 0.85]
- **Core Use Case Layer** — concept_use_case, concept_creditaccountusecasesupport, concept_creditaccountoutput, concept_clean_architecture [INFERRED 0.85]
- **Transactional Outbox Pattern** — changelog_004_create_outbox_events_outbox_events, changelog_006_create_outbox_consumers_outbox_consumers, changelog_007_create_outbox_deliveries_outbox_deliveries [INFERRED 0.85]
- **Credit Account Summary Read Model Projection** — changelog_005_create_credit_account_summary_credit_account_summary, changelog_008_create_projection_checkpoints_projection_checkpoints, changelog_006_create_outbox_consumers_credit_account_summary_projector [INFERRED 0.85]
- **Idempotency Aggregate Version Lifecycle** — changelog_003_refine_idempotency_records_idempotency_records, changelog_003_refine_idempotency_records_aggregate_version, changelog_010_drop_idempotency_aggregate_version_drop_idempotency_aggregate_version [INFERRED 0.85]

## Communities (51 total, 20 thin omitted)

### Community 0 - "Projection & Outbox Delivery"
Cohesion: 0.05
Nodes (26): Duration, OutboxDelivery, ProjectionCheckpoint, Optional, Override, OutboxDeliveryRepository, OutboxEventLoader, ProjectionCheckpointRepository (+18 more)

### Community 1 - "Acceptance Tests & Boot"
Cohesion: 0.05
Nodes (24): AcceptanceHttpClient, AcceptanceTestContext, CreditAccountStepDefinitions, Before, resolveMockitoAgentPath(), CreditAccountApplication, TestCreditAccountEventSourcingApplication, Dado (+16 more)

### Community 2 - "Use Case & Domain Tests"
Cohesion: 0.07
Nodes (27): BeforeEach, CreditAccountEventSourcingApplicationTests, CreditAccountTest, AssignCreditLimitInput, AssignCreditLimitOutput, AuthorizePurchaseInput, ChangeCreditLimitInput, OpenCreditAccountInput (+19 more)

### Community 3 - "Domain Identifiers & Value Objects"
Cohesion: 0.17
Nodes (6): ReleasePurchaseAuthorizationInput, Instant, AuthorizationId, CreditAccountId, Money, OutboxConsumer

### Community 4 - "Credit Account Aggregate"
Cohesion: 0.20
Nodes (6): CreditAccount, CreditAccountEvent, CreditLimitChanged, PaymentReceived, PurchaseAuthorizationReleased, List

### Community 5 - "REST Request DTOs"
Cohesion: 0.13
Nodes (10): AssignCreditLimitRequest, AuthorizationResponse, CapturePurchaseRequest, CreditAccountResponse, OpenCreditAccountRequest, PageResponse, ReleasePurchaseAuthorizationRequest, GetMapping (+2 more)

### Community 6 - "Account Listing & Pagination"
Cohesion: 0.16
Nodes (8): ListCreditAccountsInput, ListCreditAccountsOutput, AuthorizationSummary, CreditAccountSummaryPage, CreditAccountSummaryPageRequest, CreditAccountSummaryRepository, ListCreditAccountsUseCase, ListCreditAccountsUseCaseTest

### Community 7 - "Database Schema"
Cohesion: 0.16
Nodes (21): idempotency_records.aggregate_version, idempotency_records, outbox_events.aggregate_version, outbox_events, outbox_events.processing_attempts, credit_account_summary, credit_account_summary.last_event_id, credit_account_summary.projected_version (+13 more)

### Community 8 - "Account Query Use Case"
Cohesion: 0.19
Nodes (6): GetCreditAccountInput, GetCreditAccountOutput, SummaryNotFoundException, CreditAccountSummary, GetCreditAccountUseCase, GetCreditAccountUseCaseTest

### Community 9 - "Event Sourcing Design Docs"
Cohesion: 0.24
Nodes (17): Aggregate-Applied Domain Events, CreditAccount Aggregate, CreditAccountUseCaseSupport, Event Sourcing, Event Store, event_store Table, Optimistic Locking, RestConfiguration (+9 more)

### Community 10 - "Async Projection Design"
Cohesion: 0.24
Nodes (18): credit_account_summary, CreditAccountSummaryProjector, ListCreditAccountsUseCase, Outbox Pattern, outbox_deliveries, outbox_events, projection_checkpoints, Projection Configuration (+10 more)

### Community 11 - "Authorization Exceptions"
Cohesion: 0.11
Nodes (6): AuthorizationAlreadyExistsException, AuthorizationNotOpenException, CreditLimitNotAssignedException, DomainException, InsufficientAvailableLimitException, InvalidMoneyException

### Community 13 - "REST Exception Handling"
Cohesion: 0.23
Nodes (6): ErrorResponse, AccountAlreadyExistsException, InvalidPageSizeException, ExceptionHandler, ResponseEntity, RestExceptionHandler

### Community 14 - "Use Case Support"
Cohesion: 0.26
Nodes (7): Function, I, O, Transactional, CommandExecutor, CreditAccountUseCaseSupport, ExecutionResult

### Community 15 - "Architecture Plans"
Cohesion: 0.19
Nodes (15): Clean Architecture Use Cases, Lombok Boilerplate Reduction, Ports and Adapters Architecture, @Service Stereotype, Spring Component Discovery, Versioned Event Types, Plans 2026 06 01 Credit Account Event Sourcing Implementation, Plans 2026 06 01 Java 25 Postgresql 18 Upgrade (+7 more)

### Community 16 - "Authorize & Payment REST DTOs"
Cohesion: 0.19
Nodes (4): BigDecimal, AuthorizePurchaseRequest, AuthorizePurchaseResponse, ReceivePaymentRequest

### Community 17 - "Quality Gateway"
Cohesion: 0.35
Nodes (13): ArchUnit Fitness Functions, Checkstyle, Error Prone, PITest Mutation Testing, PMD, Quality Gateway, SpotBugs, Plans 2026 06 02 Quality Gateway Gap Closure Implementation (+5 more)

### Community 18 - "Async Projection & Infrastructure"
Cohesion: 0.35
Nodes (12): Asynchronous Read Model Projection, CQRS, Outbox Pattern, Production Outbox Projection, Docker Compose Local PostgreSQL, Plans 2026 06 04 Local Postgres Docker Compose, Plans 2026 06 10 Async Read Model Projection, Plans 2026 06 12 Production Outbox Projection (+4 more)

### Community 21 - "Capture Purchase Use Case"
Cohesion: 0.31
Nodes (4): CapturePurchaseInput, CapturePurchaseOutput, CapturePurchaseUseCase, CapturePurchaseUseCaseTest

### Community 23 - "Conflict Exceptions"
Cohesion: 0.25
Nodes (4): ConcurrencyConflictException, IdempotencyConflictException, RuntimeException, Throwable

### Community 24 - "Shell API Scripts"
Cohesion: 0.31
Nodes (6): api.sh script, api_get(), api_post(), pretty_json(), require_command(), require_tools()

### Community 25 - "Acceptance Testing Design"
Cohesion: 0.39
Nodes (8): Cucumber Acceptance Testing, Cucumber, minVersion Read-Your-Writes, ProjectionNotReadyResponse, Plans 2026 06 11 Acceptance Coverage Expansion Plan, Plans 2026 06 11 Acceptance Testing Implementation, Acceptance Testing Coverage Expansion Design, Acceptance Testing Strategy Design

### Community 26 - "DTO Mapping"
Cohesion: 0.29
Nodes (3): CreditAccountDtoMapper, CreditAccountOutput, PurchaseAuthorizationOutput

### Community 27 - "REST Response DTOs"
Cohesion: 0.67
Nodes (7): CreditAccountController, CreditAccountDtoMapper, CreditAccountOutput, CreditAccountResponse, PageResponse, REST Response DTOs Implementation Plan, Design: DTOs Explícitos para Responses REST

### Community 28 - "Testcontainers Config"
Cohesion: 0.53
Nodes (4): Bean, TestcontainersConfiguration, PostgreSQLContainer, ServiceConnection

### Community 29 - "Idempotency DB Design"
Cohesion: 0.60
Nodes (5): Idempotency, idempotency_records Table, pg_advisory_xact_lock, Transactional Idempotency, Transactional Idempotency Replay Design

### Community 30 - "Idempotency Mechanism"
Cohesion: 0.60
Nodes (6): PostgreSQL Advisory Locks, Idempotent Commands, Transactional Idempotency Replay, Plans 2026 06 09 Idempotency Transactional Replay, Plans 2026 06 13 Idempotency Remove Aggregate Version, Idempotency Rationale

### Community 31 - "PostgreSQL Infrastructure"
Cohesion: 0.67
Nodes (5): PostgreSQL Datasource, Docker Compose, PostgreSQL, Java 25 LTS and PostgreSQL 18 Upgrade Design, Local PostgreSQL Docker Compose Design

## Knowledge Gaps
- **11 isolated node(s):** `api.sh script`, `AcceptanceSpringConfig`, `CucumberAcceptanceTest`, `OutboxConsumer`, `ArchitectureFitnessFunctions` (+6 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **20 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreditAccountId` connect `Domain Identifiers & Value Objects` to `Projection & Outbox Delivery`, `Acceptance Tests & Boot`, `Use Case & Domain Tests`, `Open Credit Account Use Case`, `Credit Account Aggregate`, `Account Listing & Pagination`, `Account Query Use Case`, `Projection Tests`, `Use Case Support`?**
  _High betweenness centrality (0.034) - this node is a cross-community bridge._
- **Why does `AuthorizationId` connect `Domain Identifiers & Value Objects` to `Authorize Purchase Use Case`, `Acceptance Tests & Boot`, `Use Case & Domain Tests`, `Credit Account Aggregate`, `REST Request DTOs`, `Account Listing & Pagination`, `Release Authorization Use Case`, `Account Query Use Case`, `Projection Tests`, `Authorize & Payment REST DTOs`, `Aggregate State Transitions`, `Capture Purchase Use Case`, `DTO Mapping`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **Are the 44 inferred relationships involving `CreditAccountOpened` (e.g. with `.open()` and `.assignCreditLimitOnOpenedAccount()`) actually correct?**
  _`CreditAccountOpened` has 44 INFERRED edges - model-reasoned connections that need verification._
- **What connects `api.sh script`, `AcceptanceSpringConfig`, `CucumberAcceptanceTest` to the rest of the system?**
  _12 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Projection & Outbox Delivery` be split into smaller, more focused modules?**
  _Cohesion score 0.05327263779527559 - nodes in this community are weakly interconnected._
- **Should `Acceptance Tests & Boot` be split into smaller, more focused modules?**
  _Cohesion score 0.05407111298127498 - nodes in this community are weakly interconnected._
- **Should `Use Case & Domain Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.07214912280701755 - nodes in this community are weakly interconnected._