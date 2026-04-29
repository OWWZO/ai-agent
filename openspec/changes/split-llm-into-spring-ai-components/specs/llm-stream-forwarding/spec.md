## ADDED Requirements

### Requirement: Stream forwarding SHALL preserve existing stream message semantics
The system SHALL preserve the current Reactor stream message semantics after replacing manual SSE parsing with Spring AI `Flux<ChatResponse>`. This includes continuing to use the stream message type already selected in `AgentContext`, and continuing to emit incremental content through the existing printer interface.

#### Scenario: Thought streams keep their current message type
- **WHEN** an Agent sets `context.setStreamMessageType(...)` before calling a streaming LLM path
- **THEN** all incremental content emitted for that request MUST use the same configured stream message type

### Requirement: Stream forwarding SHALL preserve current pacing and final flush behavior
The system SHALL continue to honor the configured `messageInterval` pacing behavior, including first-chunk delay rules, periodic flushes, and final flush of any buffered content when the stream completes.

#### Scenario: Buffered stream content is flushed on completion
- **WHEN** a streaming response completes with buffered content that has not yet been pushed
- **THEN** the remaining buffered content MUST be emitted before the final aggregated result is returned

### Requirement: Streaming text calls SHALL still return full aggregated content
For streaming text generation, the system SHALL continue returning the full aggregated response text even when incremental chunks have already been emitted to the printer.

#### Scenario: Summary stream returns a complete final answer
- **WHEN** `SummaryAgent` or any other text-only caller invokes `ask(..., stream=true, ...)`
- **THEN** the caller MUST receive the complete aggregated final text
- **AND** incremental stream pushes MUST NOT truncate or replace the final returned content

### Requirement: Streaming tool calls SHALL preserve both visible thought text and final tool-call aggregation
For streaming tool-calling flows, the system SHALL preserve the ability to emit visible thought text incrementally while also returning a final `ToolCallResponse` containing the full aggregated content and any tool calls selected by the model.

#### Scenario: Tool-thought stream still returns tool calls at completion
- **WHEN** `PlanningAgent` or `ReactImplAgent` invokes `askTool(..., stream=true, ...)` and the model produces tool calls
- **THEN** the printer MUST receive incremental thought text using the configured stream message type
- **AND** the final returned `ToolCallResponse` MUST include the aggregated content and the selected tool calls

### Requirement: Streaming migration SHALL not require protocol-level SSE parsing in business code
After the streaming migration is complete, business-layer stream handling SHALL consume Spring AI `Flux<ChatResponse>` rather than manually parsing SSE lines or provider-specific JSON chunks in the LLM business flow.

#### Scenario: Business stream handler consumes chat responses instead of raw SSE lines
- **WHEN** the migrated streaming path is executed
- **THEN** the business-layer stream handler MUST receive Spring AI chat response objects as its streaming input
- **AND** it MUST NOT depend on raw `data:` line parsing to emit content
