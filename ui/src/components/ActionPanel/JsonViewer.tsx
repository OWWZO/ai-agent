import { Button } from "@/components/ui/button";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";
import { cn } from "@/lib/utils";
import { CheckIcon, CopyIcon } from "lucide-react";
import { memo, useMemo, useState } from "react";
import ReactJsonPretty from "react-json-pretty";

const jsonPrettyTheme = {
  main:
    "margin:0;padding:0;background:transparent;color:var(--json-syntax-fg);font-family:var(--font-mono);font-size:13px;line-height:1.75;letter-spacing:-0.015em;white-space:pre-wrap;word-break:break-word;overflow-wrap:anywhere;tab-size:2",
  key: "color:var(--json-syntax-key);font-weight:500",
  string: "color:var(--json-syntax-string)",
  value: "color:var(--json-syntax-number)",
  boolean: "color:var(--json-syntax-boolean);font-weight:500",
  error: "color:var(--status-failed-text)",
};

export type JsonViewerProps = {
  data: object;
  className?: string;
};

const JsonViewerInner = memo(({ data, className }: JsonViewerProps) => {
  const raw = useMemo(() => JSON.stringify(data, null, 2), [data]);
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    // 结构化内容复制使用浏览器原生 API；不支持时保持当前状态，避免误报成功。
    if (typeof navigator === "undefined" || !navigator.clipboard?.writeText) return;
    try {
      await navigator.clipboard.writeText(raw);
      setCopied(true);
      // 复制反馈是临时状态，不应影响 JSON 数据本身的 memo 化。
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      /* ignore */
    }
  };

  const Icon = copied ? CheckIcon : CopyIcon;

  return (
    <ViewerPanelShell
      className={cn("flex h-full min-h-0 flex-col rounded-none shadow-none", className)}
      bodyClassName="min-h-0 flex-1 overflow-hidden bg-[var(--chat-surface)] p-0"
      headerRight={
        <Button
          aria-label={copied ? "Copied" : "Copy JSON"}
          className={cn(
            "h-7 w-7 shrink-0 rounded-md bg-transparent text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]",
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
      <div className="h-full min-h-0 overflow-auto px-4 py-3 sm:px-5 sm:py-4">
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
