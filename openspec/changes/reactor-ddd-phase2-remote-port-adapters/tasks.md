## 1. Inventory outbound transport ownership

- [ ] 1.1 Map every in-scope direct HTTP / SSE / WebClient / OkHttp call across `QdrantService`, `MultiAgentServiceImpl`, common remote tools, and MCP runtime classes
- [ ] 1.2 Group the call sites by capability and identify the domain command, result, and streaming-event contracts each one needs

## 2. Define remote ports and adapter contracts

- [ ] 2.1 Add domain ports under `domain/adapter/port/` for Qdrant, file tool, code interpreter, data analysis, deep search, multimodal execution, and MCP runtime access
- [ ] 2.2 Define domain-friendly request, response, and streaming callback contracts that avoid leaking transport DTOs or client classes
- [ ] 2.3 Establish infrastructure adapter package structure and shared transport utilities that remain fully inside `infrastructure`

## 3. Implement infrastructure adapters

- [ ] 3.1 Implement `IQdrantPort` and `IMcpRuntimePort` production adapters, including client construction, headers, authentication, and status translation
- [ ] 3.2 Implement production adapters for file tool, code interpreter, data analysis, deep search, and multimodal remote execution capabilities
- [ ] 3.3 Add adapter-level tests for request serialization, error translation, and streaming event translation

## 4. Refactor domain services and tools

- [ ] 4.1 Refactor `QdrantService` and `MultiAgentServiceImpl` to delegate outbound integrations through the new ports
- [ ] 4.2 Refactor in-scope common tools and MCP runtime consumers so domain classes keep only business mapping and result interpretation
- [ ] 4.3 Remove direct transport client construction and transport-specific failure handling from the in-scope Reactor domain packages

## 5. Verify and document the boundary

- [ ] 5.1 Add structural checks that fail if direct transport clients or transport callback types return to the in-scope domain packages
- [ ] 5.2 Run focused regressions for Qdrant access, remote tool execution, MCP runtime resolution, and streaming remote flows through the new adapters
- [ ] 5.3 Update root and module `CLAUDE.md` files to document the Phase 2C remote-port boundary and the split of business logic versus transport ownership
