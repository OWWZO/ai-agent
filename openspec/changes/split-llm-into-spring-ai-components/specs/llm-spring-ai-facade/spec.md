## ADDED Requirements

### Requirement: LLM facade contract SHALL remain stable during Spring AI migration
The system SHALL preserve the existing Reactor `LLM` usage contract while migrating the underlying model invocation to Spring AI. This includes keeping the `new LLM(modelName, llmErp)` construction pattern, preserving the `ask(...)` and `askTool(...)` method signatures, and keeping `ToolCallResponse` fields and meanings stable for all current Agent callers.

#### Scenario: Existing Agent callers keep using the same LLM API
- **WHEN** `PlanningAgent`、`ReactImplAgent`、`ExecutorAgent`、`SummaryAgent` or `LlmSessionMemorySummaryGenerator` invoke `LLM`
- **THEN** they MUST continue to construct `LLM` the same way and consume the same return types without upstream API changes

### Requirement: Function-call generation SHALL not move tool execution out of Agent control
For `function_call` mode, the system SHALL use Spring AI only to produce assistant text and tool call arguments. The system MUST NOT allow Spring AI to execute tools internally, and MUST continue returning tool calls for `BaseAgent.executeTool()/executeTools()` to process.

#### Scenario: Native tool calling returns tool calls without executing them
- **WHEN** an Agent calls `askTool(...)` in `function_call` mode and the model decides to use one or more tools
- **THEN** the returned `ToolCallResponse` MUST contain the tool calls
- **AND** no tool side effects SHALL occur until the Agent explicitly executes the returned tool calls

### Requirement: LLM facade SHALL replay assistant/tool history into Spring AI messages
The system SHALL preserve multi-turn message replay semantics when converting domain messages into Spring AI messages. Assistant messages with tool calls MUST be replayed as assistant tool-call history, and tool result messages MUST be replayed as tool responses linked to the original tool call ID and tool name.

#### Scenario: Tool result history is restored from prior assistant tool calls
- **WHEN** conversation history contains an assistant tool call message followed by one or more tool result messages
- **THEN** the Spring AI prompt history MUST preserve the original tool call IDs
- **AND** each tool result MUST be replayed with the correct tool name resolved from prior assistant tool-call history

### Requirement: Spring AI invocation SHALL continue honoring LLM settings compatibility
The system SHALL keep honoring existing `LLMSettings` inputs used by Reactor `LLM`, including model name, base URL, interface path, max tokens, temperature, function-call mode, and compatible extra request parameters required by the current OpenAI-compatible gateway.

#### Scenario: Existing gateway-specific request settings remain effective
- **WHEN** a model invocation is created from `LLMSettings`
- **THEN** the resulting Spring AI model and chat options MUST carry forward the configured model identity and request parameters needed for the current gateway behavior

### Requirement: Compatibility branches SHALL remain available during phased migration
The system SHALL keep a controlled compatibility path for branches that are not yet migrated or that fail validation during rollout, including `struct_parse` and any legacy branch explicitly retained for rollback.

#### Scenario: New function-call path can fall back during rollout
- **WHEN** the Spring AI path encounters model resolution, message conversion, or response mapping failures during a migrated phase
- **THEN** the system MUST support switching back to the retained legacy branch for that phase without changing upstream Agent behavior
