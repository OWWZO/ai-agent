import ReactJsonPretty from "react-json-pretty";
import { cn } from "@/lib/utils";

export type ToolJsonValue = Record<string, unknown> | unknown[];

const jsonTheme = {
  main:
    "margin:0;padding:0;background:transparent;color:var(--json-syntax-fg);font-family:var(--font-mono);font-size:12px;line-height:1.65;letter-spacing:0;white-space:pre-wrap;word-break:break-word;overflow-wrap:anywhere;tab-size:2",
  key: "color:var(--json-syntax-key);font-weight:500",
  string: "color:var(--json-syntax-string)",
  value: "color:var(--json-syntax-number)",
  boolean: "color:var(--json-syntax-boolean);font-weight:500",
  error: "color:var(--status-failed-text)",
};

function parseJsonValue(value: unknown): ToolJsonValue | undefined {
  if (value && typeof value === "object") {
    return value as ToolJsonValue;
  }
  if (typeof value !== "string" || !value.trim()) {
    return undefined;
  }

  try {
    const parsed = JSON.parse(value);
    if (parsed && typeof parsed === "object") {
      return parsed as ToolJsonValue;
    }
    if (typeof parsed === "string" && parsed.trim()) {
      const nested = JSON.parse(parsed);
      return nested && typeof nested === "object"
        ? (nested as ToolJsonValue)
        : undefined;
    }
  } catch {
    return undefined;
  }
  return undefined;
}

export function parseToolJson(value: unknown): ToolJsonValue | undefined {
  return parseJsonValue(value);
}

export const ToolJsonBlock: ReactorType.FC<{
  data: ToolJsonValue;
  className?: string;
}> = ({ data, className }) => (
  <div className={cn("kimi-tool-json", className)} data-testid="tool-json-block">
    <ReactJsonPretty
      data={data as object}
      space={2}
      theme={jsonTheme}
      themeClassName="__tool-inline-json__"
    />
  </div>
);

ToolJsonBlock.displayName = "ToolJsonBlock";
