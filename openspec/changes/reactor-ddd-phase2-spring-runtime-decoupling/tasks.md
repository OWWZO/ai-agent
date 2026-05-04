## 1. Inventory Spring runtime residue

- [ ] 1.1 Map every in-scope `SpringContextHolder`, `ApplicationContext`, and `getBean(...)` usage across the Reactor domain runtime
- [ ] 1.2 Classify remaining `domain` `@Configuration` / `@Bean` declarations into move, delete, or approved-transitional buckets and document the rationale

## 2. Define explicit runtime seams

- [ ] 2.1 Introduce the minimal runtime resolver, registry, or dependency-bundle contracts needed for ai-client, handler, tool, and config access in `domain`
- [ ] 2.2 Move `AgentHandlerConfig` and related runtime bean assembly into `ai-agent-station-study-app`, wiring the new runtime contracts there
- [ ] 2.3 Route `ReactorConfig` through constructor injection or provider interfaces instead of static global access

## 3. Refactor strategies, agents, and helpers

- [ ] 3.1 Refactor `AbstractArmorySupport`, `AbstractExecuteSupport`, `FlowAgentExecuteStrategy`, and dependent node-selection logic to use explicit registries
- [ ] 3.2 Refactor `LLM`, `PlanningAgent`, `ExecutorAgent`, `ReactImplAgent`, `SummaryAgent`, and shared helpers such as `ToolCollection` / `McpTool` onto the injected runtime seam
- [ ] 3.3 Delete `SpringContextHolder` and remove in-scope `ApplicationContext` fields and imports from `domain`

## 4. Verify and document the boundary

- [ ] 4.1 Add structural checks that fail on `SpringContextHolder`, direct `ApplicationContext.getBean(...)`, and unauthorized `domain` runtime assembly residue
- [ ] 4.2 Run focused runtime regressions covering handler wiring, flow execution, agent construction, and tool callback paths
- [ ] 4.3 Update root and module `CLAUDE.md` files with the Phase 2B runtime-boundary rules and any explicitly approved transitional items
