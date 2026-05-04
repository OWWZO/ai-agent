## ADDED Requirements

### Requirement: Reactor domain SHALL retain only query semantics and port contracts
The system SHALL keep business query semantics in the domain layer while removing technical data-engine execution mechanics from it. Domain-owned classes MAY include semantic query models such as `SqlModel` and `WhereCondition`, but the domain MUST NOT own connection pools, data sources, catalog loaders, dialect factories, or equivalent execution-kernel types after Phase 2D.

#### Scenario: Structural scan confirms technical kernel ownership left domain
- **WHEN** the Reactor domain module is scanned after Phase 2D completion
- **THEN** it MUST contain no JDBC connection-pool factories, data-source wrappers, catalog loaders, or dialect-factory implementations
- **AND** any remaining query-related types in `domain` MUST represent semantic models or explicit port contracts only

### Requirement: Infrastructure SHALL own JDBC execution, catalog, and dialect implementations
The system SHALL place JDBC connection factories, connection pools, vendor dialect implementations, catalog loaders, JDBC provider executors, and other technical data-engine runtime classes in infrastructure-owned packages.

#### Scenario: Infrastructure owns the production data-engine kernel
- **WHEN** the production data-engine implementation is inspected after Phase 2D
- **THEN** the JDBC execution kernel, catalog implementation, and dialect implementation classes MUST belong to the infrastructure module package space
- **AND** domain services MUST reach those capabilities through ports rather than direct type ownership

### Requirement: SQL parsing implementation SHALL be separated from semantic query models
The system SHALL treat SQL parsing, parser configuration, and dialect-specific formatting as technical implementation concerns. If domain services need parsed query meaning, they MUST receive domain semantic models through explicit seams rather than calling technical parser utilities directly.

#### Scenario: Domain services consume semantic output without parser ownership
- **WHEN** a domain service needs parsed SQL meaning after Phase 2D
- **THEN** it MUST interact with semantic query models or an explicit parser-analysis port
- **AND** it MUST NOT directly depend on technical parser or dialect helper implementations from `domain`

### Requirement: Data-engine-facing domain services SHALL use explicit ports instead of technical kernel classes
The system SHALL require data-engine-facing domain services to depend on domain ports for query execution, metadata retrieval, catalog access, and related index-side effects rather than on JDBC providers, connection configs, or technical helper utilities.

#### Scenario: Services stop depending on technical data-engine classes
- **WHEN** services such as `DataAgentService`, `ChatModelInfoService`, or `ColumnValueSyncService` are inspected after Phase 2D
- **THEN** those services MUST depend on explicit domain-facing ports for data-engine capabilities
- **AND** they MUST NOT directly instantiate or inject technical JDBC execution-kernel classes from `domain`

### Requirement: Shared technical helpers SHALL not remain domain-owned
The system SHALL remove domain ownership of shared technical helpers such as `JdbcUtils`, `ESUtil`, and `HttpUtils`. Each helper SHALL either move into infrastructure as an internal implementation detail or disappear because its consumers now use explicit ports.

#### Scenario: Utility ownership no longer leaks infrastructure concerns into domain
- **WHEN** the Reactor domain module is inspected after Phase 2D
- **THEN** it MUST NOT own shared helper classes whose primary responsibility is JDBC setup, transport execution, Elasticsearch index operations, or similar technical orchestration
- **AND** any remaining capability that needs those behaviors MUST reach them through ports or infrastructure-local helpers

### Requirement: Phase 2D verification SHALL lock the technical data-engine boundary
The system SHALL prove that the technical data engine has left the domain layer. Acceptance MUST include structural boundary checks and representative regressions across query semantics and supported vendor execution paths.

#### Scenario: Structural checks fail if technical data-engine classes reappear in domain
- **WHEN** Phase 2D boundary verification runs
- **THEN** it MUST fail if `domain` regains connection-pool, catalog-loader, dialect-factory, or equivalent technical execution ownership

#### Scenario: Representative regressions execute through the new port-and-adapter path
- **WHEN** focused regressions exercise query metadata retrieval, semantic query handling, or vendor-specific execution flows after Phase 2D
- **THEN** they MUST run through the new domain-port and infrastructure-adapter path
- **AND** they MUST be able to detect package, wiring, or vendor-behavior regressions introduced by the migration
