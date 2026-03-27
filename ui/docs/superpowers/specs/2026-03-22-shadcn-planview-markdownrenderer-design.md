# Design: Replace PlanView & MarkdownRenderer CodeBlock with shadcn Components

**Date:** 2026-03-22
**Scope:** `PlanView/PlanView.tsx` + `ActionPanel/MarkdownRenderer.tsx`

---

## Section 1: PlanView/PlanView.tsx

### Replacement: shadcn `Plan` (Collapsible Card)

**State & controlled mode:**
```tsx
const [open, setOpen] = useState(false);
// forwardRef handlers update this state:
// openPlanView → setOpen(true)
// closePlanView → setOpen(false)
// togglePlanView → setOpen(v => !v)

<Plan open={open} onOpenChange={setOpen} isStreaming={isStreaming}>
```
Both `open` and `onOpenChange` are required for controlled mode — user clicks on `PlanTrigger` fire `onOpenChange`.

**isStreaming logic:**
```tsx
const isStreaming = Boolean(plan && !plan.stepStatus?.some(s => s === 'completed'));
```
Drives the `PlanTitle` shimmer effect during active task execution.

**Stage list inside PlanContent:**
```tsx
<PlanContent>
  {stages?.map((name, index) => (
    <div key={name} className="flex items-center gap-2 py-1">
      {getStatusIcon(stepStatus?.[index])}
      <div>
        <div>{name}</div>
        <div className="text-xs text-muted-foreground">{steps?.[index]}</div>
      </div>
    </div>
  ))}
</PlanContent>
```
`getStatusIcon` is imported from existing `./config` — no change needed.

**ResizeObserver removal:** Safe — Radix Collapsible handles expand/collapse animation natively via CSS. The ResizeObserver + manual `style.height` is no longer needed.

**Files modified:** `src/components/PlanView/PlanView.tsx` only.
**Files kept unchanged:** `PlanItem.tsx`, `Dot.tsx`, `config.tsx`, `index.ts` — their files remain, only their usage inside `PlanView.tsx` is removed (they may be used elsewhere or kept for future use).

**Deleted from PlanView.tsx:** `ResizeObserver`, `wrapRef`, `throttle`, `useToggle`, Ant Design `Timeline`, `PlanItem` import, `Dot` usage, `showStageIndex/showStageStatus/showStage` variables.

---

## Section 2: ActionPanel/MarkdownRenderer.tsx

### Replacement: shadcn `CodeBlock` + `CodeBlockCopyButton`

**Import alias to avoid collision:**
```tsx
import { CodeBlock as ShadcnCodeBlock, CodeBlockCopyButton } from "@/components/ai-elements/code-block";
import type { BundledLanguage } from "shiki";
import { bundledLanguages } from "shiki";
```

**Safe language resolution:**
```tsx
const rawLang = match[1];
const safeLanguage = (rawLang in bundledLanguages ? rawLang : 'text') as BundledLanguage;
```
Prevents shiki from throwing on unknown language identifiers (e.g. `vue`, `plaintext`).

**Children serialization:**
ReactMarkdown passes code content as a `ReactNode` array. Flatten to string:
```tsx
const codeString = Array.isArray(children)
  ? children.join('')
  : typeof children === 'string'
  ? children
  : String(children);
```

**Race condition:** shadcn `CodeBlock` uses a `mounted` ref in its `useEffect` cleanup — when `code` prop changes, cleanup resets `mounted=false` so the stale promise resolve is ignored. No additional handling needed.

**New CodeBlock component:**
```tsx
const CodeBlock: ReactorType.FC<{ inline?: boolean }> = ({ inline, className, children }) => {
  const match = /language-(\w+)/.exec(className || '');

  if (match?.[1] === 'mermaid') {
    return <Mermaid>{children}</Mermaid>;
  }

  if (!inline && match) {
    const rawLang = match[1];
    const safeLanguage = (rawLang in bundledLanguages ? rawLang : 'text') as BundledLanguage;
    const codeString = Array.isArray(children)
      ? children.join('')
      : typeof children === 'string'
      ? children
      : String(children);

    return (
      <ShadcnCodeBlock code={codeString.trim()} language={safeLanguage}>
        <CodeBlockCopyButton />
      </ShadcnCodeBlock>
    );
  }

  return <code className={className}>{children}</code>;
};
```

**Kept unchanged:** `ReactMarkdown`, `remark-gfm`, `Mermaid`, `Empty`, `usePanelContext`, `scrollToBottom` effect.

**Deleted from MarkdownRenderer.tsx:** `SyntaxHighlighter` import, `react-syntax-highlighter` usage, `CopyOutlined` import, `copyText`/`showMessage` copy logic inside old `CodeBlock`.

**Files modified:** `src/components/ActionPanel/MarkdownRenderer.tsx` only.

---

## Dependencies

- `shiki` — already installed
- `motion/react` — already installed
- No new packages needed
- `react-syntax-highlighter` package stays (may be used elsewhere), only removed from this file
