# Prompt Cache Memory Log Design

## Decision

This design starts from the runtime `Memory` class as the sole context contract
and records only the messages the model actually sees. It uses an independent
memory-log persistence model.

Execution Ledger remains the only execution fact source and UI replay source.
The new memory log is a separate derived projection used only to restore the
next model request.

## Storage

Three new tables form one append-only memory log:

- `ai_agent_prompt_memory_stream`: one stream control row for a session, agent
  scope, static prompt contract, and deterministic tool contract. It owns the
  active-request lease and the last committed turn sequence.
- `ai_agent_prompt_memory_turn`: one request delta header. It records request
  and ledger run identities, `turn_seq`, status, baseline sequence, and the
  number of newly appended messages.
- `ai_agent_prompt_memory_message`: one exact runtime `Message` per row. It
  stores the turn-local sequence, role, text, image payload, tool-call id, and
  unmodified assistant tool-call payload.

Storage is O(n): a request writes only the messages appended to `Memory` while
that request runs. It never copies the historical message prefix.

## Stream Identity

A stream is `(session_id, scope, prompt_contract_id, tool_contract_id)`.
Initial scopes are `react`, `plan`, `executor`, and `summary`.

Different scopes do not share a stream. They use different static prompts and
model protocols, so sharing their messages would reduce both correctness and
cache reuse.

The stream row provides a durable lease. One scope accepts only one active
request. A second request is queued or rejected before it can hydrate a stale
prefix. The lease is a short update to a control row, not a database
transaction held during model streaming.

## Request Lifecycle

1. Resolve the scope and the static prompt/tool contract identifiers.
2. Acquire the stream lease for the request id.
3. Query committed memory messages ordered by `turn_seq, seq_no`.
4. Copy them into a new runtime `Memory` and record its size as the baseline.
5. Append request-specific runtime context as a normal `USER` message only
   when it has content. Append the actual user question as the final `USER`
   message.
6. Run the agent normally. It appends real `ASSISTANT` and `TOOL` messages in
   the original runtime order.
7. On completion, write only `Memory[baseline..end]` as one turn delta, publish
   the turn, advance the stream sequence, and release the lease atomically.

The next request repeats steps 1 through 6 and therefore recreates the exact
previous message prefix before adding its own suffix.

## Static Prompt Contract

System prompts contain only stable role, policy, and continuation rules. They
must not contain history, current query, date, request id, session id, file
state, or request-specific base/SOP content.

Date and files are serialized into a canonical runtime-context user message.
The current query is a separate final user message. A request-specific base or
SOP value follows the same rule. A changed static role prompt or tool schema
creates a new prompt/tool contract id, which intentionally starts a new cache
prefix instead of corrupting an existing one.

Tools are rendered and sent in a deterministic order. Their schema uses a
canonical JSON representation. Assistant tool-call arguments and matching tool
responses preserve their original values and call ids.

The runtime does not fabricate a next-step user message after a tool response.
The static system prompt instructs the model to evaluate tool results and then
continue with another tool call or produce the final response.

## Completion Rules

Only a valid message prefix can be published. An assistant message that calls
tools is published only when all referenced tool responses are present. A
failure or stop discards an incomplete assistant-tool suffix, but can publish
an earlier complete prefix. Ledger status remains authoritative for the actual
run outcome.

RAG and ChatClient Advisor prompt rewriting are not changed in this work.

## Acceptance Criteria

- No default Agent system prompt contains cross-turn history or request-time
  query, date, file, or session values.
- A second request restores an ordered USER, ASSISTANT, TOOL sequence equal to
  the first request's model-visible `Memory` prefix.
- A request persists only its own appended messages.
- Tool call ids, tool arguments, and tool-result order survive round-trip.
- One stream cannot commit two competing request branches.
- React, Plan, Executor, and Summary use separate streams.
- No runtime path reads a predecessor memory projection.
- Ledger-backed history and UI replay remain unchanged.
