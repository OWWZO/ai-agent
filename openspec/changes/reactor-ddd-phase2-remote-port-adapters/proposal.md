## Why

The Reactor domain still constructs `OkHttpClient`, `WebClient`, and SSE transports directly inside domain services and tools. That mixes business protocol mapping with HTTP client code, request headers, status handling, and remote DTO details, which blocks clean DDD boundaries and makes tool behavior difficult to substitute or test.

## What Changes

- Define explicit remote capability ports in `domain/adapter/port/`, including at minimum `IQdrantPort`, `IFileToolPort`, `ICodeInterpreterPort`, `IDataAnalysisPort`, `IDeepSearchPort`, `IMultiModalPort`, and `IMcpRuntimePort`.
- Move HTTP / SSE / WebClient / OkHttp client construction, remote DTOs, headers, authentication, and HTTP status handling into `infrastructure/adapter/port/` implementations.
- Refactor `QdrantService`, `MultiAgentServiceImpl`, in-scope `agent/tool/common/*` classes, and `agent/tool/mcp/runtime/*` runtime builders so domain classes keep only business protocol, input/output mapping, and result interpretation.
- Extract streaming remote interactions behind domain-facing callback or stream contracts rather than raw transport types.
- Add structural and regression verification to prevent direct remote transport code from returning to the Reactor domain.

## Capabilities

### New Capabilities
- `reactor-ddd-remote-port-boundary`: Define how Reactor remote integrations are expressed as domain ports and implemented by infrastructure-owned HTTP / SSE / MCP adapters.

### Modified Capabilities
None.

## Impact

- Affects `ai-agent-station-study-domain` and `ai-agent-station-study-infrastructure`, especially `QdrantService`, `MultiAgentServiceImpl`, common remote tools, and MCP runtime client construction.
- Changes the runtime integration path for Qdrant, file operations, code interpreter, data analysis, deep search, multimodal execution, and MCP runtime access.
- Does not change public API or persistence schema, but it does change outbound remote-call composition and error handling ownership, so transport contract tests and focused regressions are required.
