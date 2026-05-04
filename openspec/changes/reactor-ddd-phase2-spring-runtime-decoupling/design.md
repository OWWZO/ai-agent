## Context

Phase 1 moved low-risk Reactor wiring out of `domain`, and Phase 2A is converging persistence ownership. The next structural blocker is Spring runtime coupling: Reactor domain classes still call `ApplicationContext.getBean(...)` directly, and multiple runtime paths still depend on `SpringContextHolder`.

Current examples include:

- `AbstractArmorySupport`, `AbstractExecuteSupport`, and `FlowAgentExecuteStrategy` keeping `ApplicationContext` fields and named bean lookups.
- `LLM`, `PlanningAgent`, `ExecutorAgent`, `ReactImplAgent`, and `SummaryAgent` pulling `ReactorConfig` or Spring-managed collaborators from `SpringContextHolder`.
- `ToolCollection`, `McpTool`, and several common tools inheriting the same service-locator pattern.
- `AgentHandlerConfig` still assembling runtime beans from within the `domain` module.

This prevents clean construction in tests, hides dependency shape behind bean names, and keeps runtime-framework decisions mixed with business rules. The goal of Phase 2B is to remove Spring service locator behavior from the domain runtime while preserving current agent semantics and allowing `ReactorConfig` to remain a documented transitional config contract.

## Goals / Non-Goals

**Goals:**

- Remove `SpringContextHolder` and in-scope `ApplicationContext.getBean(...)` usage from Reactor domain runtime paths.
- Replace named bean lookups with explicit dependency injection, runtime resolver interfaces, or registry abstractions owned by the domain contract.
- Move `AgentHandlerConfig` and remaining runtime bean assembly from `domain` into `app`.
- Keep `ReactorConfig` usable during the transition, but only through explicit injection or provider seams.
- Add structural and regression verification so the Spring boundary cannot silently regress.

**Non-Goals:**

- Do not migrate outbound HTTP / SSE / WebClient / OkHttp implementations; those belong to the remote-port extraction change.
- Do not move JDBC, SQL parser, dialect, or catalog technical kernels; those belong to the data-engine extraction change.
- Do not redesign agent behavior, prompts, or tool business semantics.
- Do not require every Spring annotation in `domain` to disappear if an explicitly approved transitional configuration remains; the requirement is that runtime assembly and service locator behavior no longer live there.

## Decisions

### 1. Introduce explicit runtime resolver contracts instead of bean-name lookups

**Decision**

In-scope runtime consumers SHALL receive collaborators through explicit interfaces such as resolver ports, registries, or immutable dependency bundles rather than through `ApplicationContext` or raw bean-name strings.

**Rationale**

- Bean-name lookups hide real dependencies and make construction order opaque.
- A narrow runtime resolver contract keeps the domain independent from Spring while preserving dynamic selection behavior where it is actually needed.
- This fits the Reactor runtime better than replacing every lookup with one-off constructor parameters, because several strategies still need controlled late binding.

**Alternatives considered**

- Keep `ApplicationContext` but wrap it in a helper: this preserves service locator behavior and does not improve testability.
- Replace every dynamic lookup with large constructors only: this removes Spring coupling but can create oversized constructors and duplicate selection logic.

### 2. App owns runtime assembly; domain owns runtime contracts

**Decision**

`ai-agent-station-study-app` SHALL own bean assembly classes such as `AgentHandlerConfig` and any runtime factory that wires Spring-managed collaborators together. `domain` SHALL retain only the interfaces and behavior contracts needed by Reactor runtime services.

**Rationale**

- Assembly is a framework concern, not a domain concern.
- Moving assembly to `app` aligns with the existing Phase 1 direction and makes the bean graph inspectable in one place.

**Alternatives considered**

- Keep configuration classes in `domain` and document them as exceptions: this preserves the same structural ambiguity.
- Move assembly into `infrastructure`: runtime wiring here is application composition, not persistence or gateway implementation.

### 3. Use a typed runtime dependency bundle for agent construction

**Decision**

Agent classes such as `LLM`, `PlanningAgent`, `ExecutorAgent`, `ReactImplAgent`, and `SummaryAgent` SHALL consume typed collaborator bundles or factories instead of reading Spring globals on demand.

**Rationale**

- These classes are often instantiated as part of runtime orchestration rather than plain singleton injection.
- A typed bundle exposes the dependency surface once and can carry transitional collaborators like `ReactorConfig` without leaking Spring APIs.

**Alternatives considered**

- Let each agent fetch collaborators lazily from a registry singleton: that simply recreates `SpringContextHolder` with a different name.
- Inject Spring-managed beans directly into every call site: workable, but it spreads assembly concerns across too many places.

### 4. ReactorConfig remains transitional, but only as an injected contract

**Decision**

`ReactorConfig` MAY remain in its current transitional module location during this change, but its values MUST reach runtime consumers through constructor injection, provider interfaces, or dependency bundles. No in-scope domain class may pull it from static global context.

**Rationale**

- The repository already documents `ReactorConfig` as a deferred physical migration item.
- Removing static access now provides most of the testability and boundary benefit without forcing an unnecessary physical move in the same change.

**Alternatives considered**

- Move `ReactorConfig` immediately: broader scope with little extra boundary value for this phase.
- Keep static global reads until a later phase: this leaves the most damaging runtime coupling in place.

### 5. Shared runtime helpers must converge on the same seam, not special-case exceptions

**Decision**

Helpers such as `ToolCollection`, `McpTool`, and any tool/runtime utility that still depends on `SpringContextHolder` MUST be refactored onto the same injected runtime seam used by the primary agent classes.

**Rationale**

- If helpers stay on the old seam, `SpringContextHolder` cannot actually be removed.
- A single seam avoids a fragmented migration where main agents are clean but supporting paths still reintroduce hidden container access.

**Alternatives considered**

- Clean only the explicitly listed classes: faster initially, but it fails the intended domain-level grep and leaves runtime construction inconsistent.

### 6. Verification combines structural scans with representative runtime regressions

**Decision**

Phase 2B verification SHALL include both structural checks and focused runtime regressions:

- structural checks for `SpringContextHolder`, `ApplicationContext.getBean(...)`, and unauthorized `@Configuration` / `@Bean` residue in `domain`;
- focused runtime tests for handler assembly, flow execution, agent construction, and tool callback wiring.

**Rationale**

- Compiling successfully does not prove the service locator is gone.
- Pure grep checks do not prove that the new injected runtime graph still works.

**Alternatives considered**

- Rely only on grep scans: too weak for runtime safety.
- Rely only on integration tests: too weak for enforcing the boundary contract.

## Risks / Trade-offs

- [Hidden service-locator calls remain in secondary runtime helpers] -> Use targeted scans over the full Reactor domain package, not only the headline classes.
- [Dynamic bean selection loses flexibility during refactor] -> Preserve selection semantics behind explicit registries keyed by the same business identifiers currently used for bean naming.
- [App-owned runtime assembly becomes too broad] -> Keep assembly classes limited to composition and move no business logic with them.
- [ReactorConfig transition lingers too long] -> Document the approved transitional status explicitly and prohibit any new static access from being introduced.

## Migration Plan

1. Inventory every `SpringContextHolder`, `ApplicationContext`, and `getBean(...)` usage across the Reactor domain runtime and group them by collaborator type.
2. Define the minimal runtime resolver / registry / dependency-bundle contracts in `domain`.
3. Move `AgentHandlerConfig` and related runtime assembly into `app`, wiring the new contracts there.
4. Refactor `AbstractArmorySupport`, `AbstractExecuteSupport`, `FlowAgentExecuteStrategy`, and dependent runtime nodes to use explicit registries.
5. Refactor `LLM`, `PlanningAgent`, `ExecutorAgent`, `ReactImplAgent`, `SummaryAgent`, and shared runtime helpers onto injected runtime dependencies.
6. Delete `SpringContextHolder`, run structural boundary checks, execute focused regressions, and update module documentation.

**Rollback**

- If runtime assembly regressions appear, roll back to the last verified registry or factory step rather than reintroducing `SpringContextHolder`.
- Since this phase changes composition rather than schema, rollback is code-only and does not require data migration.

## Open Questions

- Should the runtime seam use several narrowly scoped resolvers, or one composite `ReactorRuntimeDependencies` object with typed accessors?
- Does `AgentHandlerConfig` move as one file, or should its responsibilities be split into smaller app-owned configuration classes while migrating?
- Are there any non-Reactor domain paths still relying on the same service-locator utilities that should be excluded from this change and handled separately?
