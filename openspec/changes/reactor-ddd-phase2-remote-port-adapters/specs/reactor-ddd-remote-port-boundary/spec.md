## ADDED Requirements

### Requirement: Reactor domain external integrations SHALL be expressed through domain ports
The system SHALL require in-scope Reactor external capabilities to be accessed through explicit domain ports defined under `domain/adapter/port/`. At minimum, the change SHALL introduce ports for Qdrant, file-tool execution, code-interpreter execution, data-analysis execution, deep-search execution, multimodal execution, and MCP runtime access.

#### Scenario: Domain ports exist for the in-scope external capabilities
- **WHEN** the Phase 2C domain boundary is inspected
- **THEN** the in-scope external capabilities MUST be represented by explicit domain-facing port interfaces
- **AND** domain services or tools in scope MUST depend on those ports rather than on transport client types

#### Scenario: Domain callers stay transport-agnostic
- **WHEN** a domain caller invokes an in-scope remote capability after Phase 2C
- **THEN** the caller MUST interact through domain commands, callbacks, or result types
- **AND** the caller MUST NOT need to know whether the production adapter uses OkHttp, WebClient, SSE, or another transport implementation

### Requirement: Infrastructure SHALL own transport clients and remote protocol details
The system SHALL place HTTP / SSE / WebClient / OkHttp client construction, remote DTO serialization, request headers, authentication, and HTTP status handling in infrastructure-owned adapters rather than in `domain`.

#### Scenario: Transport details leave the domain module
- **WHEN** the in-scope Reactor domain packages are inspected after Phase 2C
- **THEN** they MUST NOT construct transport clients or inspect transport-specific HTTP status objects directly
- **AND** those responsibilities MUST reside in infrastructure adapter implementations

#### Scenario: MCP runtime construction is infrastructure-owned
- **WHEN** the system creates or resolves MCP runtime clients after Phase 2C
- **THEN** the client construction path MUST be owned by infrastructure code behind `IMcpRuntimePort`
- **AND** `domain` MUST NOT build `WebClient`-backed MCP runtime clients directly

### Requirement: Domain tools and services SHALL retain only business mapping and result interpretation
The system SHALL keep in-scope domain tools and services responsible for business validation, command shaping, and interpretation of remote results into domain outcomes. They SHALL NOT build raw requests, attach headers, or own transport-specific failure handling.

#### Scenario: Common tools stop building raw remote requests
- **WHEN** `FileTool`, `CodeInterpreterTool`, `DataAnalysisTool`, `DeepSearchTool`, `MultiModalAgent`, `ReportTool`, or equivalent in-scope tool classes are inspected after Phase 2C
- **THEN** those classes MUST keep only business mapping and result interpretation logic for remote calls
- **AND** they MUST NOT own raw transport client construction, header assembly, or HTTP status branching

#### Scenario: Qdrant and multi-agent services use ports instead of direct remote clients
- **WHEN** `QdrantService` or `MultiAgentServiceImpl` executes an external operation after Phase 2C
- **THEN** the domain service MUST delegate the outbound integration through a domain port
- **AND** any remote protocol or client-specific exception handling MUST be translated by infrastructure adapters

### Requirement: Streaming remote interactions SHALL use domain-defined event contracts
The system SHALL represent streaming remote interactions through domain-defined callbacks, event sinks, or stream result contracts rather than leaking raw SSE or transport callback types into Reactor domain logic.

#### Scenario: Deep-search and multi-agent streaming stay domain-oriented
- **WHEN** an in-scope streaming capability emits progress, partial output, completion, or failure after Phase 2C
- **THEN** the domain layer MUST observe those updates through domain-defined event contracts
- **AND** the infrastructure adapter MUST translate raw transport events into those domain events

### Requirement: Phase 2C verification SHALL lock the remote integration boundary
The system SHALL prove that direct transport ownership has left the Reactor domain. Acceptance MUST include structural scans and representative regressions that execute through the new infrastructure adapters.

#### Scenario: Structural scans fail if transport code returns to domain
- **WHEN** Phase 2C boundary verification runs over the in-scope Reactor domain packages
- **THEN** the checks MUST fail if transport client construction, raw transport callback types, or direct HTTP status handling reappears there

#### Scenario: Representative regressions run through production adapters
- **WHEN** focused regressions exercise Qdrant access, remote tools, MCP runtime resolution, or streaming remote flows
- **THEN** the regressions MUST execute through the new port-and-adapter path
- **AND** they MUST be able to catch wiring, serialization, status-translation, or streaming-translation regressions introduced by the migration
