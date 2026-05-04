## ADDED Requirements

### Requirement: Legacy Reactor HTTP entrypoints SHALL live in the trigger module without contract drift
The system SHALL expose the existing legacy Reactor HTTP routes from the `trigger` module rather than from `domain`. Migrating these controllers MUST preserve the current URL paths, HTTP methods, request payload types, representative response types, and delegation behavior for all existing `/1/**` and `/data/**` entrypoints in scope for Phase 1.

#### Scenario: Trigger controllers expose the full existing route set
- **WHEN** the application inspects the migrated `ReactorController` and `DataAgentController`
- **THEN** the controllers MUST expose the same Phase 1 legacy routes currently provided by the Reactor `/1/**` and `/data/**` endpoints
- **AND** no route in that in-scope legacy set may be removed or renamed during the migration

#### Scenario: Representative endpoint delegation remains unchanged after migration
- **WHEN** a caller invokes representative migrated endpoints such as Reactor health checks, Reactor streaming agent queries, model-info lookup, vector recall, ES recall, API chat query, NL2SQL lookup, model listing, or preview data
- **THEN** each endpoint MUST delegate to the same underlying service dependency as before
- **AND** the endpoint MUST preserve the existing response shape expected by current callers

### Requirement: Low-risk Reactor Spring wiring SHALL move to the app module
The system SHALL move low-risk Reactor Spring wiring classes out of `domain` and into the `app` module. Phase 1 scope includes `ReplayProjectorAutoConfiguration`, `Es7HighLevelClientConfig`, and `DataAgentInitRunner`. Their migration MUST preserve existing bean availability and initialization behavior.

#### Scenario: Replay projector beans load from app-owned configuration
- **WHEN** the application context initializes replay projector related beans after the Phase 1 migration
- **THEN** those beans MUST be contributed from configuration classes under the `app` module package space
- **AND** the resulting bean graph MUST remain functionally equivalent for current replay consumers

#### Scenario: Deferred shared configuration remains outside Phase 1 migration
- **WHEN** the Phase 1 migration completes
- **THEN** `ReactorConfig` MUST remain in its current transitional location and behavior
- **AND** no Phase 1 requirement may depend on physically moving or redesigning that shared configuration contract

### Requirement: Domain ledger services SHALL depend on execution-ledger repository ports instead of DAOs
The system SHALL define execution-ledger read and write repository ports in `domain` and SHALL require `AgentExecutionRecorderImpl` and `ExecutionLedgerQueryServiceImpl` to depend on those ports rather than on any ledger `*Dao` type directly.

#### Scenario: Domain ledger services no longer declare DAO fields
- **WHEN** Phase 1 ledger services are inspected at the field level
- **THEN** `AgentExecutionRecorderImpl` and `ExecutionLedgerQueryServiceImpl` MUST NOT declare fields whose types are ledger DAO interfaces
- **AND** their collaborator dependencies MUST be expressed through execution-ledger repository ports and existing higher-level collaborators only

#### Scenario: Ledger service behavior remains available through the new ports
- **WHEN** a caller uses the domain ledger services to create runs, finish runs, record LLM or tool invocations, record artifacts, or query run and session history
- **THEN** the service MUST continue to provide those behaviors through the repository-port-backed implementation
- **AND** upstream domain callers MUST NOT need to know whether the underlying persistence is implemented with MyBatis DAOs

### Requirement: Infrastructure SHALL own the production execution-ledger repository adapters
The system SHALL implement the production execution-ledger read and write repository adapters in the `infrastructure` module. In Phase 1 those adapters MAY reuse the current ledger DAO interfaces, ledger entities, and Mapper XML files as transitional persistence contracts, but the DAO details MUST be contained inside the adapter layer.

#### Scenario: Production ledger repositories reside in infrastructure
- **WHEN** the production execution-ledger repository implementations are loaded
- **THEN** the read and write repository classes MUST belong to the `infrastructure` module package space
- **AND** domain services MUST reach ledger persistence through those adapters rather than directly through DAO injection

#### Scenario: Transitional persistence contracts remain in place during Phase 1
- **WHEN** Phase 1 boundary convergence is completed
- **THEN** the existing ledger DAO interfaces, ledger entities, and ledger Mapper XML contracts MAY still exist in their current transitional locations
- **AND** Phase 1 MUST NOT require moving or renaming those persistence artifacts to satisfy the new repository seam

### Requirement: Phase 1 boundary convergence SHALL preserve deferred seams explicitly
The system SHALL preserve the explicitly deferred seams outside the scope of Phase 1, including `SessionContextMemoryServiceImpl`, `ToolOutputWriterImpl`, `ToolOutputReaderImpl`, `WorkspaceImageGenerationServiceImpl`, ledger persistence type relocation, and Mapper XML namespace migration. Phase 1 acceptance MUST confirm that these concerns are not silently pulled into the change.

#### Scenario: Deferred seams remain unchanged while Phase 1 passes regression
- **WHEN** Phase 1 implementation and regression checks are complete
- **THEN** the deferred runtime and persistence seams MUST remain outside the changed scope unless a separate follow-up change authorizes them
- **AND** Phase 1 verification MUST be able to distinguish between successful boundary convergence and accidental expansion into deferred areas
