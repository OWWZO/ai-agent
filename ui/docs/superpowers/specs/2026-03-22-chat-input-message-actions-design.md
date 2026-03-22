# Design: Chat Input Attachment + Message Action Buttons

**Date:** 2026-03-22
**Scope:** `GeneralInput/index.tsx` + `Dialogue/index.tsx` + `ChatView/index.tsx`

---

## Goal

1. Add `+` attachment button to `GeneralInput` footer (file upload via PromptInput built-in)
2. Add copy / regenerate / more action buttons below AI messages in `Dialogue`
3. Wire `onRegenerate` callback in `ChatView` to re-send the last user message

---

## Section 1: GeneralInput/index.tsx

### File Upload
- Add `accept="image/*,application/pdf,.txt,.md"` and `multiple` props to `PromptInput`
- `handleSubmit` signature changes from `({ text }) => void` to `({ text, files }) => void`
- Files are passed through to `send` callback as `inputInfo.files`

### Footer Structure (when `showBtn=true`)
```
Left:  PromptInputActionMenu (+ button) | 深度研究 button (when !isChatMode)
Right: enter tip | send button
```

### PromptInputActionMenu for "+"
```tsx
<PromptInputActionMenu>
  <PromptInputActionMenuTrigger>
    <PromptInputButton size="icon-sm" variant="ghost">
      <PlusIcon className="size-4" />
    </PromptInputButton>
  </PromptInputActionMenuTrigger>
  <PromptInputActionMenuContent>
    <PromptInputActionAddAttachments label="上传附件" />
  </PromptInputActionMenuContent>
</PromptInputActionMenu>
```

### Attachments Display (above textarea, inside PromptInputBody)
```tsx
<PromptInputAttachments>
  {(file) => <PromptInputAttachment key={file.id} data={file} />}
</PromptInputAttachments>
```
Shown inside `PromptInputBody`, above `PromptInputTextarea`. Only renders when files exist.

### Footer when `showBtn=false`
No change — "+" button only shown when `showBtn=true` (home page + ChatView with showBtn=false keeps current behavior).

Wait — `showBtn=false` is the ChatView input. The "+" button should appear in ChatView too.
Revised: "+" button appears regardless of `showBtn`. It moves to a minimal position in the footer left side when `showBtn=false`.

**Revised footer logic:**
- Always show `+` button on left
- Show 深度研究 button only when `showBtn=true && !isChatMode`
- Right: enter tip + send button (unchanged)

---

## Section 2: Dialogue/index.tsx

### New prop
```tsx
type Props = {
  // ... existing props
  onRegenerate?: () => void;
};
```

### MessageActions placement
Rendered below the AI response message, only when `chat.response && !chat.loading`:

```tsx
{chat.response && !chat.loading ? (
  <MessageActions className="ml-1 mt-1">
    <MessageAction tooltip="复制" onClick={handleCopy}>
      {copied ? <CheckIcon className="size-4" /> : <CopyIcon className="size-4" />}
    </MessageAction>
    <MessageAction tooltip="重新生成" onClick={onRegenerate} disabled={!onRegenerate}>
      <RefreshCwIcon className="size-4" />
    </MessageAction>
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <MessageAction tooltip="更多">
          <MoreHorizontalIcon className="size-4" />
        </MessageAction>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuItem onClick={handleCopy}>复制原文</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  </MessageActions>
) : null}
```

### Copy state
Local `useState<boolean>(false)` (copied) in `Dialogue`. On copy: `navigator.clipboard.writeText(chat.response)` → set copied=true → reset after 2s via setTimeout.

### Imports needed
```tsx
import { MessageActions, MessageAction } from "@/components/ai-elements/message";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { CopyIcon, CheckIcon, RefreshCwIcon, MoreHorizontalIcon } from "lucide-react";
```

---

## Section 3: ChatView/index.tsx

### onRegenerate handler
```tsx
const handleRegenerate = useMemoizedFn(() => {
  const last = chatList.current[chatList.current.length - 1];
  if (!last || loading) return;
  sendMessage({
    message: last.query,
    outputStyle: product?.type,
    deepThink: inputInfoProp.deepThink,
  });
});
```

### Pass to Dialogue
```tsx
<Dialogue
  chat={chat}
  deepThink={inputInfoProp.deepThink}
  changeTask={changeTask}
  changeFile={changeFile}
  changePlan={changePlan}
  onRegenerate={handleRegenerate}
/>
```

---

## Files Modified
1. `src/components/GeneralInput/index.tsx`
2. `src/components/Dialogue/index.tsx`
3. `src/components/ChatView/index.tsx`

## Dependencies
- All imports already available in the project
- No new packages needed
