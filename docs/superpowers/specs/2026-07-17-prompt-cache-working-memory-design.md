# Prompt Cache Working Memory Design

## Goal

Replace the legacy cross-turn history text injected into system prompts with a
durable, append-only working-memory message stream. The next request restores
the exact model-visible message sequence into runtime `Memory` before adding
the new request. This keeps the request prefix stable for provider prompt
caching.

The change applies to the Reactor Java execution path. The Execution Ledger
remains the only execution source of truth. Working-memory tables are a
derived LLM-context projection and are never used for UI history replay.

## Scope

The projection persists every model-visible runtime message in original order:

- `USER`
- `ASSISTANT`, including complete tool-call payloads
- `TOOL`, including the original `toolCallId`

System messages are not persisted in the memory stream. They are reconstructed
from a static, versioned prompt contract for each agent scope.

## Memory Streams

A stream is identified by `(session_id, memory_scope)`. Initial scopes are:

- `react`
- `plan`
- `executor`
- `summary`

Scopes must not share messages because their system prompts, tool sets, and
runtime protocols differ. A scope also carries a prompt-contract version and a
deterministic tool-definition fingerprint. A changed contract starts a new
stream version instead of silently mixing incompatible messages.

## O(n) Storage Model

`ai_agent_working_memory_turn` stores one append transaction for a request:

- session and scope identity
- request and ledger run identity
- monotonically increasing `turn_seq`
- `base_turn_seq` read at request start
- state: `BUILDING`, `READY`, or `INVALID`
- prompt-contract version, tool fingerprint, message count, and token estimate

`ai_agent_working_memory_message` stores only messages newly appended during
that request. Its canonical order is `(turn_seq, seq_no)`. It preserves role,
content, image payload, tool-call id, and structured tool-call JSON without
lossy text conversion.

Each persisted row is an immutable delta. Rehydration reads all `READY` deltas
for one stream in canonical order and constructs an equivalent `List<Message>`
for `Memory.preload`.

## Runtime Flow

1. Resolve the stream identity from the selected agent scope and static prompt
   contract.
2. Acquire a short-lived per-stream execution lease. A second request for the
   same stream is queued or rejected as busy; it must not execute from a stale
   memory prefix.
3. Read every `READY` message delta ordered by `turn_seq, seq_no` and hydrate a
   new runtime `Memory`.
4. Capture `baselineMessageCount` immediately after hydration.
5. Construct the agent with a static system prompt and the hydrated memory.
6. Run the agent. Runtime code appends only real user, assistant, and tool
   messages using the normal `Memory` API.
7. At completion, persist `messages[baselineMessageCount..end]` as the turn
   delta, then atomically mark the turn `READY` and release the execution lease.

The lease is not a database transaction held while an LLM streams. It is a
durable ownership record with expiry/heartbeat and request-id idempotency.

## Prompt Cache Contract

The message prefix must be deterministic for a stable stream:

1. The system prompt contains only stable role and policy text.
2. Conversation history is never rendered into the system prompt.
3. The current user request is appended as the final `USER` message.
4. Dynamic date, request id, session id, and file context are appended to the
   appropriate runtime `USER` message, not the system prompt.
5. Tool definitions have deterministic ordering and canonical JSON.
6. Assistant tool-call payloads and matching tool responses round-trip without
   normalization that changes model-visible content.

The runtime must not add a synthetic next-step user message after a tool
response. The fixed system prompt instead contains the stable instruction to
evaluate tool results and either continue with another tool call or return the
final answer. A genuinely dynamic stage instruction is represented as a normal
persisted user message only when it changes model-visible input.

An agent's static system prompt and tool fingerprint are part of its stream
identity. A configuration change can cause one intentional cache miss, but it
cannot corrupt the existing stream.

## Failure and Stop Semantics

Only a protocol-valid message prefix may become `READY`:

- normal assistant text is valid;
- an assistant message with tool calls is valid only after all referenced tool
  response messages are present;
- incomplete assistant-tool tails are removed before persistence;
- a successful final assistant response is retained on failed or stopped runs
  when the prefix remains valid.

The ledger run always records the actual run status. Working memory records the
last valid model context and never replaces ledger facts.

## PlanSolve Ownership

The planner, executor, and summary agents each hydrate and persist only their
own scope. Child executor message copies remain local implementation details.
Only messages merged into the parent executor `Memory` become part of the
executor stream. Summary task history and the current query are user messages,
not dynamic summary system-prompt content.

## Deferred Advisor Scope

RAG and ChatClient Advisor prompt rewriting are explicitly outside this change.
This phase must not alter Advisor behavior. The cache contract applies to the
Reactor runtime paths that assemble their model-visible messages from `Memory`.

## Compatibility and Migration

`SessionContextMemoryService.buildHistoryDialogue` remains available only for
explicit legacy/debug mode. Default React and PlanSolve paths must not set
`AgentRequest.historyDialogue` and must not call history injection helpers.

Existing legacy system-prompt history is not migrated. A stream without READY
working-memory turns starts empty and builds its first delta from the new run.

## Acceptance Criteria

- Default React and PlanSolve requests do not place history, current query,
  date, or file context in a system prompt.
- Tool-result continuation does not add a synthetic next-step `USER` message.
- A second request hydrates the exact ordered USER, ASSISTANT, and TOOL prefix
  emitted by the first request, including tool call ids and arguments.
- Persisted message row count grows by only the current request delta.
- Concurrent requests to one `(session_id, memory_scope)` cannot commit
  divergent turn sequences.
- A failed run persists no incomplete tool-call suffix.
- Ledger-backed conversation history and UI replay behavior remain unchanged.
- Focused tests verify round-trip equality, ordering, tool pairing, failure
  truncation, static prompt construction, and concurrent request protection.
