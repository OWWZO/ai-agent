import ReactJsonPretty from "react-json-pretty";
import { memo } from "react";
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

/**
 * JSON 展示块。
 *
 * `memo` 是这里的关键：`ReactJsonPretty` 每次渲染都要把整个对象
 * `JSON.stringify` 一遍再逐 token 上色（实测 2000 条约 15ms）。调用方
 * （`ToolOutputBlock`）用内容哈希缓存解析结果，拿到的是**同一个对象引用**，
 * 只有配上 `memo` 才能真正跳过重渲染 —— 两者缺一不可。
 */
export const ToolJsonBlock: ReactorType.FC<{
  data: ToolJsonValue;
  className?: string;
}> = memo(({ data, className }) => (
  <div className={cn("kimi-tool-json", className)} data-testid="tool-json-block">
    <ReactJsonPretty
      data={data as object}
      space={2}
      theme={jsonTheme}
      themeClassName="__tool-inline-json__"
    />
  </div>
));

ToolJsonBlock.displayName = "ToolJsonBlock";
