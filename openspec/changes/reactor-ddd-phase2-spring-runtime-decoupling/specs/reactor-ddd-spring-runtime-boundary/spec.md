## ADDED Requirements

### Requirement: Reactor domain runtime SHALL not use Spring service-locator access
The system SHALL remove `SpringContextHolder` and in-scope `ApplicationContext.getBean(...)` access from the Reactor domain runtime. After Phase 2B, domain runtime collaborators MUST be resolved without static Spring globals or ad hoc bean-name lookups from `domain`.

#### Scenario: Structural scan finds no approved service-locator residue
- **WHEN** the Reactor domain runtime is scanned after Phase 2B completion
- **THEN** in-scope classes MUST contain zero `SpringContextHolder` references
- **AND** no approved runtime consumer may call `ApplicationContext.getBean(...)` directly from `domain`

#### Scenario: Runtime behavior remains available without static Spring globals
- **WHEN** representative Reactor flows create agents, execute handlers, or dispatch strategy steps
- **THEN** the flows MUST continue to resolve the same effective collaborators as before
- **AND** that resolution MUST happen through injected dependencies, resolver ports, or registries rather than static Spring globals

### Requirement: In-scope Reactor strategies and agents SHALL consume explicit runtime dependency contracts
The system SHALL require `AbstractArmorySupport`, `AbstractExecuteSupport`, `FlowAgentExecuteStrategy`, `LLM`, `PlanningAgent`, `ExecutorAgent`, `ReactImplAgent`, and `SummaryAgent` to obtain runtime collaborators through explicit constructor-injected dependencies, dependency bundles, or typed registry interfaces.

#### Scenario: Strategy classes stop holding ApplicationContext fields
- **WHEN** `AbstractArmorySupport`, `AbstractExecuteSupport`, and `FlowAgentExecuteStrategy` are inspected after Phase 2B
- **THEN** those classes MUST NOT keep `ApplicationContext` fields for named bean lookup
- **AND** any dynamic collaborator selection they still require MUST be expressed through explicit registry or resolver contracts

#### Scenario: Agent classes receive typed runtime collaborators
- **WHEN** `LLM`, `PlanningAgent`, `ExecutorAgent`, `ReactImplAgent`, and `SummaryAgent` are constructed after Phase 2B
- **THEN** each class MUST receive the collaborators it needs through typed runtime contracts
- **AND** no class in this in-scope set may fetch `ReactorConfig` or other Spring-managed collaborators from a global holder at call time

### Requirement: Reactor runtime bean assembly SHALL live in the app module
The system SHALL treat agent handler wiring and runtime bean composition as application assembly concerns. `AgentHandlerConfig` and other in-scope bean-assembly classes MUST live in `ai-agent-station-study-app` rather than `ai-agent-station-study-domain`.

#### Scenario: Agent handler assembly is app-owned
- **WHEN** the application context loads Reactor handler and runtime assembly beans after Phase 2B
- **THEN** the in-scope assembly configuration MUST be contributed from the `app` module package space
- **AND** the `domain` module MUST NOT own handler bean assembly classes for that same runtime path

#### Scenario: Domain configuration residue is explicitly constrained
- **WHEN** the `domain` module is inspected for `@Configuration` and `@Bean` declarations after Phase 2B
- **THEN** any remaining declaration MUST be an explicitly approved transitional item documented by the change
- **AND** no remaining declaration may be used to hide runtime service-locator behavior or handler assembly

### Requirement: ReactorConfig SHALL remain injectable but not globally fetched
The system MAY keep `ReactorConfig` as a documented transitional contract, but in-scope runtime consumers MUST obtain it through constructor injection, provider interfaces, or typed runtime bundles rather than through static global Spring access.

#### Scenario: ReactorConfig values remain available through explicit seams
- **WHEN** runtime components need planning, executor, summary, MCP, or tool-related Reactor configuration after Phase 2B
- **THEN** they MUST receive those values through explicit injected seams
- **AND** no in-scope component may call a static holder to obtain `ReactorConfig`

### Requirement: Phase 2B verification SHALL lock the Spring runtime boundary against regression
The system SHALL provide structural and behavioral verification that the Spring runtime boundary has converged. Acceptance MUST include automated checks for service-locator removal and representative regressions for runtime assembly behavior.

#### Scenario: Boundary checks fail if service-locator behavior returns
- **WHEN** Phase 2B verification runs its structural checks
- **THEN** the checks MUST fail if `SpringContextHolder`, direct `ApplicationContext.getBean(...)`, or unauthorized runtime assembly configuration reappears in `domain`

#### Scenario: Runtime regressions exercise the new assembly path
- **WHEN** focused runtime regressions execute agent construction, flow execution, handler dispatch, or tool callback wiring
- **THEN** the regressions MUST run through the new injected or registry-backed runtime path
- **AND** they MUST be able to detect bean-graph or collaborator-resolution regressions introduced by the refactor
