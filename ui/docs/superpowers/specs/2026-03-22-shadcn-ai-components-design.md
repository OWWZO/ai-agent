# Design: Replace Dialogue & ChatView with shadcn AI Components

**Date:** 2026-03-22
**Scope:** `Dialogue/index.tsx` + `ChatView/index.tsx`
**Approach:** Option B — Replace Dialogue visual layer + ChatView scroll container

---

## Goal

Replace hand-crafted chat UI in `Dialogue/index.tsx` and the manual scroll mechanism in `ChatView/index.tsx` with the shadcn AI components already present in `src/components/ai-elements/`. All existing business logic (tool types, click handlers, task routing) is preserved.

---

## Constraints

- `DataDialogue` is **not** in scope — only `Dialogue/index.tsx` (main dialogue)
- All ToolItem business logic is preserved: `changeTask`, `changePlan`, `changeFile` handlers stay intact
- `PlanSection` and `ConclusionSection` have no shadcn equivalents — kept as-is
- `LoadingDot` and `LoadingSpinner` kept as-is
- `chat.tip` and `chat.files` kept as-is (no shadcn equivalent)

---

## Component Mapping

### `Dialogue/index.tsx`

| Current | Replacement | Notes |
|---|---|---|
| `chat.files` attachment list | **Unchanged** | Keep existing `AttachmentList` |
| User bubble (`chat.query`) | `Message from="user"` + `MessageContent` | Override via `className="group-[.is-user]:bg-[#4040FFB2] group-[.is-user]:text-white"` to preserve brand color |
| `chat.tip` | **Unchanged** | Keep existing div |
| AI response (`chat.response`) | `Message from="assistant"` + `MessageContent` + `MessageResponse` | `<MessageResponse>{chat.response}</MessageResponse>` — Streamdown renders Markdown |
| `chat.thought` gray block (only when `!isReactType`) | `Reasoning` + `ReasoningTrigger` + `ReasoningContent` | `isStreaming={chat.loading && !chat.response}`, condition `!isReactType && chat.thought` preserved |
| `chat.planList` (only when `!isReactType`) | **Unchanged** (`PlanSection`) | No shadcn equivalent |
| `ToolItem` case `tool_thought` | `Task` + `TaskTrigger` + `TaskContent` + `TaskItem` | See structure below |
| Other ToolItem cases (plan, browser, task_summary, default) | **Unchanged** | Chip UI and click handlers preserved |
| `ConclusionSection` | **Unchanged** | No shadcn equivalent |
| `LoadingDot` | **Unchanged** | |
| `LoadingSpinner` in TimeLine | **Unchanged** | |

#### Task component structure for `tool_thought`:
```tsx
<Task>
  <TaskTrigger title="思考过程" />
  <TaskContent>
    <TaskItem>{tool.toolThought}</TaskItem>
  </TaskContent>
</Task>
```

### `ChatView/index.tsx`

| Current | Replacement | Notes |
|---|---|---|
| `overflow-auto` div + `chatRef` + `scrollToTop()` in `renderMultAgent` | `Conversation` + `ConversationContent` + `ConversationScrollButton` | `scrollToTop` sets `scrollTop = scrollHeight` (scroll to bottom). `StickToBottom` replicates this exactly + adds scroll button |
| `overflow-auto` div + `chatRef` + `scrollToTop()` in `renderDataAgent` | Same `Conversation` wrapper | DataDialogue content unchanged |
| `scrollToTop` import | Removed | |
| `chatRef` useRef | Removed | |
| 3× `scrollToTop(chatRef.current!)` call sites | Removed | In `handleMessage` of both SSE handlers |

---

## Files Modified

1. `src/components/Dialogue/index.tsx`
2. `src/components/ChatView/index.tsx`

## Files NOT Modified

- `src/components/Dialogue/DataDialogue.tsx`
- `src/components/ai-elements/*`
- All other components

---

## Key Implementation Details

### User Message Color Override
`MessageContent` uses `group-[.is-user]:bg-secondary` internally. To override, pass the same variant prefix:
```tsx
<MessageContent className="group-[.is-user]:bg-[#4040FFB2] group-[.is-user]:text-white">
```
This ensures tailwind-merge correctly replaces the variant-scoped class.

### AI Response with Markdown
```tsx
<Message from="assistant">
  <MessageContent>
    <MessageResponse>{chat.response}</MessageResponse>
  </MessageContent>
</Message>
```
`MessageResponse` accepts `children: string` and renders via Streamdown.

### Reasoning for `chat.thought`
```tsx
<Reasoning isStreaming={chat.loading && !chat.response}>
  <ReasoningTrigger />
  <ReasoningContent>{chat.thought}</ReasoningContent>
</Reasoning>
```
- `isStreaming` drives the auto-open/auto-close behavior (closes 1s after streaming ends)
- `ReasoningContent` children must be a string — `chat.thought` is a string ✓
- Wrapped in `{!isReactType && chat.thought ? (...) : null}` to preserve existing condition

### Conversation Scroll Container
```tsx
<Conversation className="flex-1 mb-[36px]">
  <ConversationContent>
    {chatList.current.map((chat) => (
      <div key={chat.requestId}>
        <Dialogue ... />
      </div>
    ))}
  </ConversationContent>
  <ConversationScrollButton />
</Conversation>
```
Note: `ConversationContent` has default `gap-8 p-4` — adjust with className if needed to match existing spacing.

---

## Dependencies

- `use-stick-to-bottom` — already installed (used by `conversation.tsx`)
- `streamdown` — already installed (used by `message.tsx`, `reasoning.tsx`)
- No new packages needed
