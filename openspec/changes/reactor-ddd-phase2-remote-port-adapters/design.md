## Context

After Phase 2A and Phase 2B, the remaining major boundary leak in Reactor domain logic is outbound remote integration. Several domain classes still create and manage remote clients directly:

- `QdrantService` performs direct outbound requests.
- `MultiAgentServiceImpl` manages remote SSE behavior with `OkHttpClient`.
- Common tools such as `FileTool`, `CodeInterpreterTool`, `DataAnalysisTool`, `DeepSearchTool`, `MultiModalAgent`, and `ReportTool` still construct requests, headers, and status handling inside `domain`.
- `agent/tool/mcp/runtime/*` still owns `WebClient`-based MCP client construction.
- Some residual outbound transport may still exist inside other runtime helpers after the Spring runtime decoupling change.

This makes domain behavior depend on transport mechanics, remote DTO formats, and low-level error handling. Phase 2C separates those concerns by defining domain-facing remote ports and moving the technical implementations into infrastructure adapters.

This change also absorbs the "external dependency through ports" half of Phase 2E. The persistence / repository half remains with the persistence extraction change.

## Goals / Non-Goals

**Goals:**

- Define domain-facing remote ports for the major Reactor external capabilities.
- Move HTTP / SSE / WebClient / OkHttp client construction and protocol details into `infrastructure`.
- Keep domain tools and services focused on command mapping, domain validation, and result interpretation.
- Provide domain-friendly streaming abstractions so SSE or streaming adapters no longer leak raw transport types upward.
- Add structural and contract verification so domain-owned transport code cannot return.

**Non-Goals:**

- Do not redesign Spring bean assembly; that belongs to the Spring runtime decoupling change.
- Do not migrate JDBC / SQL / catalog technical kernels; that belongs to the data-engine extraction change.
- Do not change persistence ownership for tool outputs, session memory, or workspace images beyond using the already-defined repository seams.
- Do not change end-user tool semantics, event shapes, or REST endpoints unless required by the new adapter boundary.

## Decisions

### 1. Define one domain port per external capability, with domain-centric request and result types

**Decision**

The domain SHALL define explicit ports such as `IQdrantPort`, `IFileToolPort`, `ICodeInterpreterPort`, `IDataAnalysisPort`, `IDeepSearchPort`, `IMultiModalPort`, and `IMcpRuntimePort`. These ports SHALL expose domain-centric commands, events, and result types rather than raw `OkHttp`, `WebClient`, or transport-layer DTOs.

**Rationale**

- A capability-aligned port keeps the contract readable and testable.
- Domain-centric request/result types prevent transport details from creeping back into service logic.

**Alternatives considered**

- One giant `IRemoteGateway` interface: too broad and hard to evolve safely.
- Keeping current utility wrappers in domain: still leaks transport concerns into business logic.

### 2. Infrastructure owns remote DTOs, headers, authentication, and status translation

**Decision**

All remote client construction, request DTO serialization, header assembly, authentication, retry or timeout policy, and HTTP status translation SHALL be implemented under `infrastructure/adapter/port/` or adjacent infrastructure packages.

**Rationale**

- These are technical integration details, not domain rules.
- Centralizing them in infrastructure allows transport-level tests and clearer operational ownership.

**Alternatives considered**

- Let domain ports pass raw headers or request builders through: this breaks the boundary immediately.
- Keep transport exceptions as-is in domain: this couples the domain to external client libraries.

### 3. Domain tools keep only business protocol and result interpretation

**Decision**

In-scope domain tools and services SHALL keep only:

- business validation and command building;
- domain-friendly request / response mapping;
- interpretation of remote results into Reactor events, tool outputs, or agent observations.

They SHALL NOT build transport clients, set HTTP headers, or inspect HTTP status codes directly.

**Rationale**

- This preserves the useful domain logic while removing technical orchestration from the same class.
- It also matches the desired Phase 2E layering: business rules in domain, technical orchestration behind ports.

**Alternatives considered**

- Move the entire tool classes to infrastructure: this would also move business semantics out of the domain, which is not desired.

### 4. Streaming capabilities use domain-defined callback or event sink contracts

**Decision**

Capabilities that currently depend on SSE or streaming HTTP, such as deep search or multi-agent collaboration, SHALL expose domain-defined callback, event sink, or stream contracts. Infrastructure adapters SHALL translate raw transport events into those domain events.

**Rationale**

- Domain code should react to business events, not to `EventSource`, `WebClient`, or transport callback classes.
- This keeps streaming testable with fake adapters and deterministic event scripts.

**Alternatives considered**

- Return raw `EventSource` or `Flux` transport objects from infrastructure into domain: this leaks framework and transport choices upward.

### 5. MCP runtime client construction moves entirely behind IMcpRuntimePort

**Decision**

`agent/tool/mcp/runtime/*` client construction, `WebClient` transport selection, authentication, and error handling SHALL move behind `IMcpRuntimePort` or its supporting adapter contracts. Domain code may still decide when to ask for an MCP runtime, but it SHALL not build the client itself.

**Rationale**

- MCP runtime construction is one of the clearest examples of technical client ownership living in the wrong module.
- This boundary also prevents `WebClient` from remaining as a special-case leak after the rest of the remote migration.

**Alternatives considered**

- Leave MCP runtime as a domain exception: this weakens the entire remote port boundary.

### 6. Verification must prove both the structural boundary and the adapter contracts

**Decision**

Phase 2C verification SHALL include:

- structural scans for transport clients and transport-specific classes in the in-scope Reactor domain packages;
- focused contract tests for the infrastructure adapters;
- representative tool and service regressions running through the new ports.

**Rationale**

- The refactor is not complete if the domain still owns client construction even once.
- Adapter contract tests are needed because behavior now crosses an explicit seam.

**Alternatives considered**

- Compile-only verification: insufficient for streaming and remote error handling changes.
- Regex-only verification: insufficient for adapter correctness.

## Risks / Trade-offs

- [Ports become too chatty or mirror transport APIs] -> Design port commands and results around business intent, not HTTP verbs or raw JSON bodies.
- [Streaming migrations change event timing or cancellation behavior] -> Preserve current domain event semantics and cover cancellation / failure paths with adapter contract tests.
- [Residual direct transport remains in non-obvious helpers] -> Scan the full in-scope Reactor domain packages for `OkHttpClient`, `WebClient`, `EventSource`, and transport status handling patterns.
- [Multiple tools duplicate similar adapter code] -> Share infrastructure HTTP client utilities only inside infrastructure, never by reintroducing common transport helpers in domain.

## Migration Plan

1. Inventory every in-scope direct remote call in Reactor domain services, tools, and MCP runtime classes.
2. Define the minimal domain ports and domain command / result contracts for each remote capability.
3. Implement infrastructure adapters for Qdrant, file tool, code interpreter, data analysis, deep search, multimodal execution, and MCP runtime construction.
4. Refactor domain services and tools to call the new ports and retain only business mapping and interpretation logic.
5. Remove direct transport code from the in-scope domain packages, add structural checks, and run focused regressions.

**Rollback**

- If a remote capability regresses, roll back the corresponding adapter migration while keeping the port contract in place where possible.
- Because this phase changes transport ownership rather than persistence schema, rollback remains code-only.

## Open Questions

- Should `ReportTool` reuse `IFileToolPort`, or does it need a dedicated document-render or artifact-render port?
- Do deep-search and multi-agent streaming ports share one common event sink abstraction, or should they keep separate domain event contracts?
- If `LLM` still owns residual direct transport after Phase 2B, should that extraction happen here under a dedicated port or be deleted in favor of existing Spring AI collaborators?
