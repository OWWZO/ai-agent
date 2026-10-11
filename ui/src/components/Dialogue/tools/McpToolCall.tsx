import { memo, useMemo, useState } from "react";
import { CheckIcon, CopyIcon, PlugIcon, WrapTextIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { copyText } from "@/utils";
import { useCodePreviewWrap } from "@/hooks/useCodePreviewWrap";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { ToolJsonBlock, parseToolJson } from "./ToolJsonBlock";
import {
  formatDurationLabel,
  resolveTaskToolArg,
  resolveTaskToolName,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
} from "./toolTaskAdapter";
import { parseMcpToolName } from "./mcpToolName";

type McpToolCallProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  durationMs?: number;
  durationLabel?: string;
  stackPosition?: ToolRowStackPosition;
  defaultExpanded?: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

const iconBtn =
  "inline-flex size-6 shrink-0 items-center justify-center rounded-md text-[var(--color-text-faint)] transition-colors hover:bg-[var(--color-surface-sunken)] hover:text-[var(--color-text)]";

/**
 * MCP 工具卡（对齐 ZCode `renderers/mcp.tsx`）。
 *
 * 与 ZCode 的差异是刻意的：
 * - ZCode 把「调用参数」藏在二级折叠（调用详情）里；Reactor 直接平铺两段，
 *   因为这里没有 ZCode 的 `description` 等其它详情可放，二级折叠只会多一次点击。
 * - 结果段带**复制**与**自动换行**两个动作，换行偏好复用全局 `useCodePreviewWrap`
 *   （不要另起 useState，否则和代码查看器的偏好会分叉）。
 *
 * 这个 renderer **不打开右侧面板**（TASKS.md §2.1：ZCode 的 mcp 不开侧栏）。
 */
export const McpToolCall = memo(function McpToolCall({
  tool,
  durationMs,
  durationLabel,
  stackPosition = "single",
  defaultExpanded,
}: McpToolCallProps) {
  const rawName = resolveTaskToolName(tool);
  const identity = parseMcpToolName(rawName);
  const arg = resolveTaskToolArg(tool);
  const status = resolveTaskToolStatus(tool);
  const output = resolveTaskToolOutput(tool);
  const timing = durationLabel || formatDurationLabel(durationMs);

  const [open, setOpen] = useState(Boolean(defaultExpanded));
  const [copied, setCopied] = useState(false);
  const [wrap, , toggleWrap] = useCodePreviewWrap();

  const inputJson = useMemo(() => parseToolJson(arg), [arg]);
  const resultText = useMemo(() => output.join("\n").trim(), [output]);
  const resultJson = useMemo(() => parseToolJson(resultText), [resultText]);

  const label = identity?.toolName || rawName || "MCP";
  const server = identity?.serverKey || "";
  const hasOutput = output.length > 0;
  const expandable = hasOutput || Boolean(inputJson) || Boolean(arg.trim());

  const handleCopy = () => {
    copyText(resultText);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  };

  return (
    <ToolRow
      status={status}
      icon={<PlugIcon className="size-3.5" />}
      name={label}
      arg={!open ? server : ""}
      time={timing}
      chip={server}
      open={open}
      expandable={expandable}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onToggle={() => setOpen((v) => !v)}
    >
      {inputJson || arg.trim() ? (
        <section className="kimi-tool-mcp-section">
          <h4 className="kimi-tool-mcp-title">调用参数</h4>
          {inputJson ? (
            <ToolJsonBlock data={inputJson} />
          ) : (
            <pre className="kimi-tool-mcp-text">{arg}</pre>
          )}
        </section>
      ) : null}

      <section className="kimi-tool-mcp-section">
        <div className="kimi-tool-mcp-head">
          <h4 className="kimi-tool-mcp-title">结果</h4>
          <div className="flex shrink-0 items-center gap-0.5">
            <button
              type="button"
              className={iconBtn}
              aria-pressed={wrap}
              aria-label="自动换行"
              title={wrap ? "自动换行：已开启" : "自动换行：已关闭"}
              onClick={toggleWrap}
            >
              <WrapTextIcon className="size-3.5" />
            </button>
            <button
              type="button"
              className={iconBtn}
              aria-label="复制结果"
              title="复制结果"
              disabled={!resultText}
              onClick={handleCopy}
            >
              {copied ? (
                <CheckIcon className="size-3.5" />
              ) : (
                <CopyIcon className="size-3.5" />
              )}
            </button>
          </div>
        </div>

        {resultText ? (
          resultJson ? (
            <div
              className={cn(
                "max-h-72 overflow-auto",
                status === "error" && "kimi-tool-mcp-error"
              )}
              data-testid="mcp-result-surface"
            >
              <ToolJsonBlock data={resultJson} />
            </div>
          ) : (
            <pre
              className={cn(
                "kimi-tool-mcp-text",
                status === "error" && "kimi-tool-mcp-error",
                wrap ? "is-wrap" : "is-nowrap"
              )}
              data-testid="mcp-result-surface"
            >
              {resultText}
            </pre>
          )
        ) : (
          <p className="kimi-tool-mcp-empty" data-testid="mcp-result-surface">
            {status === "error"
              ? "调用失败"
              : status === "running"
                ? "调用中…"
                : "暂无结果"}
          </p>
        )}
      </section>
    </ToolRow>
  );
});
