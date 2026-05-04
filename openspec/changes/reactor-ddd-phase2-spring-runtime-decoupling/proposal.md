## Why

Phase 2A corrected DAO ownership, but the Reactor domain runtime still resolves collaborators through `ApplicationContext.getBean(...)` and `SpringContextHolder`. That keeps agent construction hidden behind Spring service-locator behavior, leaves `@Configuration` residue in `domain`, and prevents the Phase 2 boundary from becoming testable and explicit.

## What Changes

- Replace in-scope Spring service-locator usage in `AbstractArmorySupport`, `AbstractExecuteSupport`, `FlowAgentExecuteStrategy`, and Reactor agent/runtime classes with explicit dependency injection, domain registry interfaces, or runtime resolver ports.
- Remove `SpringContextHolder` from the domain runtime path and prohibit new static `ApplicationContext` access from Reactor domain classes.
- Move `AgentHandlerConfig` and other runtime bean-assembly concerns out of `domain` into `ai-agent-station-study-app`.
- Keep `ReactorConfig` as an approved transitional config contract if necessary, but require consumers to obtain it through constructor injection or provider interfaces rather than global static lookup.
- Add structural and regression verification for `ApplicationContext.getBean(...)`, `SpringContextHolder`, and unauthorized `@Configuration` / `@Bean` residue inside `domain`.

## Capabilities

### New Capabilities
- `reactor-ddd-spring-runtime-boundary`: Define how Reactor runtime collaborators are assembled in `app` and consumed in `domain` without Spring service-locator access.

### Modified Capabilities
None.

## Impact

- Affects `ai-agent-station-study-domain` and `ai-agent-station-study-app`, plus any runtime factory or registry wiring used to construct agents, handlers, and strategy chains.
- Touches `SpringContextHolder`, `AgentHandlerConfig`, `AbstractArmorySupport`, `AbstractExecuteSupport`, `FlowAgentExecuteStrategy`, `LLM`, `PlanningAgent`, `ExecutorAgent`, `ReactImplAgent`, `SummaryAgent`, and related runtime helpers that still delegate to Spring lookups.
- Does not change public API contracts or database schema, but does change the bean graph and runtime assembly path, so focused regression coverage is required.
