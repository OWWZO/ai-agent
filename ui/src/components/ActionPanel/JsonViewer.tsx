import { Button } from "@/components/ui/button";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";
import { cn } from "@/lib/utils";
import { CheckIcon, CopyIcon } from "lucide-react";
import { memo, useMemo, useState } from "react";
import ReactJsonPretty from "react-json-pretty";

const jsonPrettyTheme = {
  main:
    "margin:0;background:transparent;color:var(--json-syntax-fg);font-family:var(--font-mono);font-size:13px;line-height:1.75;letter-spacing:-0.015em;white-space:pre-wrap;word-break:break-word;overflow-wrap:anywhere;tab-size:2",
  key: "color:var(--json-syntax-key);font-weight:500",
  string: "color:var(--json-syntax-string)",
  value: "color:var(--json-syntax-number)",
  boolean: "color:var(--json-syntax-boolean);font-weight:500",
};

export type JsonViewerProps = {
  data: object;
  className?: string;
};

const JsonViewerInner = memo(({ data, className }: JsonViewerProps) => {
  const raw = useMemo(() => JSON.stringify(data, null, 2), [data]);
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    if (typeof navigator === "undefined" || !navigator.clipboard?.writeText) return;
    try {
      await navigator.clipboard.writeText(raw);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      /* ignore */
    }
  };

  const Icon = copied ? CheckIcon : CopyIcon;

  return (
    <ViewerPanelShell
      className={className}
      headerRight={
        <Button
          aria-label={copied ? "Copied" : "Copy JSON"}
          className={cn(
            "h-7 w-7 shrink-0 rounded-md bg-[var(--chat-surface)] text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]",
            copied && "text-[var(--success)]"
          )}
          onClick={copy}
          size="icon-sm"
          type="button"
          variant="ghost"
        >
          <Icon className="size-3.5" />
        </Button>
      }
      label="JSON"
      subtitle="Structured data"
    >
      <div
        className={cn(
          "max-h-[min(70vh,560px)] overflow-auto rounded-lg px-3 py-2.5 sm:px-4 sm:py-3",
          "shadow-[inset_0_1px_0_oklch(1_0_0_/_0.06)] dark:shadow-[inset_0_1px_0_oklch(1_0_0_/_0.04)]"
        )}
      >
        <ReactJsonPretty
          data={data}
          space={2}
          theme={jsonPrettyTheme}
          themeClassName="__json-pretty-json-view__"
        />
      </div>
    </ViewerPanelShell>
  );
});

JsonViewerInner.displayName = "JsonViewerInner";

export const JsonViewer = JsonViewerInner;
