## ADDED Requirements

### Requirement: Reactor DAO and Mapper types SHALL reside in the infrastructure module
The system SHALL place Reactor ledger, tool-output, and chat-model DAO / Mapper interfaces in the `infrastructure` module rather than in `domain`. After Phase 2A, the `domain` module MUST NOT define or own Reactor MyBatis DAO / Mapper types, including interfaces annotated as mappers or extending `BaseMapper`.

#### Scenario: Domain no longer owns Reactor DAO types
- **WHEN** the codebase is scanned after Phase 2A completion
- **THEN** `ai-agent-station-study-domain` MUST contain zero Reactor DAO / Mapper interfaces
- **AND** no Reactor type under `domain` may declare `@Mapper` or extend `BaseMapper`

#### Scenario: Infrastructure owns the migrated Reactor DAO contracts
- **WHEN** the migrated Reactor persistence layer is inspected
- **THEN** ledger, tool-output, and chat-model DAO / Mapper interfaces MUST belong to the `infrastructure` module package space
- **AND** production persistence wiring MUST resolve those interfaces from infrastructure-owned packages

### Requirement: MyBatis mapping contracts SHALL follow the migrated infrastructure DAO namespaces
The system SHALL update Reactor MyBatis XML mappings so that each mapper namespace targets the migrated infrastructure DAO type rather than the old domain mapper type. The Spring/MyBatis runtime MUST continue to load these mappings successfully after the package migration.

#### Scenario: Reactor mapper XML namespaces point to infrastructure DAO types
- **WHEN** Reactor mapper XML files are inspected after the migration
- **THEN** each namespace that previously referenced `org.wwz.ai.domain.agent.reactor.mapper.*` MUST reference the corresponding infrastructure DAO type instead
- **AND** no in-scope Reactor mapper XML may retain the old domain mapper namespace

#### Scenario: Spring can still load migrated Reactor mappers
- **WHEN** the application starts a Spring context that includes MyBatis mapper scanning
- **THEN** the Reactor mapper XML files MUST still bind successfully to their DAO interfaces
- **AND** representative Reactor persistence flows MUST remain executable without mapper-resolution errors

### Requirement: Domain services SHALL use repository ports instead of direct Reactor mapper inheritance or injection
The system SHALL require Reactor domain services to depend on repository ports rather than directly inheriting from or injecting MyBatis mapper types. At minimum, Phase 2A MUST cover the chat-model metadata services that currently expose `ServiceImpl<BaseMapper>` behavior from the domain layer.

#### Scenario: Chat-model services stop exposing MyBatis ServiceImpl as domain behavior
- **WHEN** `ChatModelInfoService` and `ChatModelSchemaService` are inspected after Phase 2A
- **THEN** those services MUST NOT extend MyBatis-Plus `ServiceImpl`
- **AND** their persistence behavior MUST be obtained through repository ports defined in the domain layer

#### Scenario: Domain callers remain persistence-implementation agnostic
- **WHEN** domain callers use chat-model metadata services to initialize metadata, clean stale metadata, query distinct models or schemas, or preview model data
- **THEN** those callers MUST continue to observe the same domain-level behavior
- **AND** they MUST NOT need to know whether MyBatis, BaseMapper, or Mapper XML are used underneath

### Requirement: Transitional Reactor services SHALL not reintroduce domain-owned mapper dependencies
The system SHALL treat `SessionContextMemoryServiceImpl`, `WorkspaceImageGenerationServiceImpl`, `ToolOutputReaderImpl`, and `ToolOutputWriterImpl` as explicit Phase 2A transition points. These implementations MAY remain technical executors for now, but they MUST NOT keep or introduce dependencies on `domain`-owned Reactor mapper types once the migration is complete.

#### Scenario: Transitional services only reference migrated infrastructure mapper types
- **WHEN** the transition-point services are inspected after Phase 2A
- **THEN** any remaining direct DAO dependency they still require MUST resolve to infrastructure-owned Reactor DAO types
- **AND** no new dependency on `org.wwz.ai.domain.agent.reactor.mapper.*` may be added

#### Scenario: Deferred seam extraction remains explicitly out of scope
- **WHEN** Phase 2A acceptance is evaluated
- **THEN** the presence of transitional technical executors MUST NOT be treated as a failure by itself
- **AND** the accepted scope MUST distinguish between “DAO ownership corrected” and “all technical executors fully abstracted,” leaving the latter to follow-up changes

### Requirement: Phase 2A verification SHALL prove boundary convergence through tests and structural checks
The system SHALL provide verification that the persistence-boundary migration is real, not nominal. Acceptance MUST include structural checks for domain mapper removal, mapper namespace migration, and representative regression coverage through production repository paths.

#### Scenario: Structural checks confirm domain mapper removal
- **WHEN** Phase 2A verification runs its boundary checks
- **THEN** the checks MUST detect any remaining Reactor mapper ownership under `domain`
- **AND** the change MUST fail verification if such ownership remains

#### Scenario: Representative regressions exercise the migrated persistence path
- **WHEN** ledger, tool-output, session-memory, workspace-image, or chat-model related regressions are executed for this change
- **THEN** the selected tests MUST run through the migrated DAO / repository path
- **AND** the tests MUST be able to catch package, namespace, or adapter wiring regressions introduced by the migration
