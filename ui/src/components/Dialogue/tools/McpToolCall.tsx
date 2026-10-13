import { memo, useMemo, useState } from "react";
import { CheckIcon, ChevronRightIcon, CopyIcon, PlugIcon, WrapTextIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { copyText } from "@/utils";
import { useCodePreviewWrap } from "@/hooks/useCodePreviewWrap";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { ToolJsonBlock, parseToolJson } from "./ToolJsonBlock";
import {
  resolveTaskToolArg,
  resolveTaskToolName,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
} from "./toolTaskAdapter";
import { isCompactMcpResult, parseMcpToolName } from "./mcpToolName";

type McpToolCallProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  stackPosition?: ToolRowStackPosition;
  defaultExpanded?: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

const iconBtn =
  "inline-flex size-6 shrink-0 items-center justify-center rounded-md text-[var(--color-foreground-subtle)] transition-colors hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-text)]";

/**
 * MCP 工具卡（对齐 ZCode `renderers/mcp.tsx`）。
 *
 * 信息层级是 ZCode 的核心设计：**结果是一级内容，调用元数据收进二级折叠**。
 * 展开后先看到结果（或错误），参数要点一下「调用详情」才出现 —— 调用 MCP 时
 * 用户关心的是返回了什么，不是传了什么。
 *
 * 另外两点与 ZCode 一致：
 * - 结果段头部带**复制**与**自动换行**两个动作；换行偏好复用全局
 *   `useCodePreviewWrap`（不要另起 useState，否则和代码查看器的偏好会分叉）。
 * - 短结果（单行、≤160 字符、非 JSON）不套代码块、也不给标题与动作按钮。
 *
 * 这个 renderer **不打开右侧面板**（TASKS.md §2.1：ZCode 的 mcp 不开侧栏）。
 */
export const McpToolCall = memo(function McpToolCall({
  tool,
  stackPosition = "single",
  defaultExpanded,
}: McpToolCallProps) {
  const rawName = resolveTaskToolName(tool);
  const identity = parseMcpToolName(rawName);
  const arg = resolveTaskToolArg(tool);
  const status = resolveTaskToolStatus(tool);
  const output = resolveTaskToolOutput(tool);

  const [open, setOpen] = useState(Boolean(defaultExpanded));
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [wrap, , toggleWrap] = useCodePreviewWrap();

  const inputJson = useMemo(() => parseToolJson(arg), [arg]);
  const resultText = useMemo(() => output.join("\n").trim(), [output]);
  const resultJson = useMemo(() => parseToolJson(resultText), [resultText]);

  // 对齐 ZCode `renderers/mcp.tsx`：类别标签固定 "MCP"，server 名走 kindDetail，
  // 两者之间用 `·` 分隔，工具名才是主文本。拆不出 server/tool 时退回原始工具名，
  // 避免出现 "MCP MCP" 这种自我重复。
  const server = identity?.serverKey || "";
  const toolName = identity?.toolName || "";
  const fallbackLabel = rawName || "MCP";
  // ZCode：单行、≤160 字符且不是 JSON 的结果走「一行文本」形态，不套代码块。
  const compactResult = isCompactMcpResult(resultText);
  const isError = status === "error";
  const hasOutput = output.length > 0;
  const hasCallDetails = Boolean(inputJson) || Boolean(arg.trim());
  const expandable = hasOutput || hasCallDetails;

  const handleCopy = () => {
    copyText(resultText);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  };

  return (
    <ToolRow
      status={status}
      icon={<PlugIcon className="size-4" />}
      name={identity ? "MCP" : fallbackLabel}
      kindDetail={server ? server : undefined}
      separator={server ? "·" : undefined}
      arg={identity ? toolName : ""}
      open={open}
      expandable={expandable}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onToggle={() => setOpen((v) => !v)}
    >
      <div className="kimi-tool-mcp-expanded" data-testid="mcp-expanded-content">
        {/* ---- 一级：结果 ---- */}
        {isError ? (
          <section className="kimi-tool-mcp-section">
            <h4 className="kimi-tool-mcp-title is-error">结果</h4>
            <p className="kimi-tool-mcp-error-box" data-testid="mcp-result-surface">
              {resultText || "调用失败"}
            </p>
          </section>
        ) : compactResult ? (
          // 短结果：一行文本，不给标题、不给动作按钮（ZCode 的 compact 分支）
          <p className="kimi-tool-mcp-compact" data-testid="mcp-result-surface">
            {resultText}
          </p>
        ) : resultText ? (
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

            {resultJson ? (
              <div className="kimi-tool-mcp-text" data-testid="mcp-result-surface">
                <ToolJsonBlock data={resultJson} />
              </div>
            ) : (
              <pre
                className={cn(
                  "kimi-tool-mcp-text",
                  wrap ? "is-wrap" : "is-nowrap"
                )}
                data-testid="mcp-result-surface"
              >
                {resultText}
              </pre>
            )}
          </section>
        ) : (
          // 无结果：沿用摘要行的生命周期文案，不另造措辞
          <p className="kimi-tool-mcp-empty" data-testid="mcp-result-surface">
            {status === "running" ? "调用中…" : "暂无结果"}
          </p>
        )}

        {/* ---- 二级：调用详情（参数） ---- */}
        {hasCallDetails ? (
          <Collapsible
            className="kimi-tool-mcp-details"
            open={detailsOpen}
            onOpenChange={setDetailsOpen}
          >
            <CollapsibleTrigger asChild>
              <button
                type="button"
                className="kimi-tool-mcp-details-trigger"
                data-testid="mcp-call-details-trigger"
              >
                <ChevronRightIcon
                  className={cn(
                    "size-3.5 shrink-0 transition-transform",
                    detailsOpen && "rotate-90"
                  )}
                />
                调用详情
              </button>
            </CollapsibleTrigger>
            <CollapsibleContent>
              <div className="kimi-tool-mcp-details-body">
                <h4 className="kimi-tool-mcp-detail-title">参数</h4>
                {inputJson ? (
                  <div className="kimi-tool-mcp-text">
                    <ToolJsonBlock data={inputJson} />
                  </div>
                ) : (
                  <pre className="kimi-tool-mcp-text is-wrap">{arg}</pre>
                )}
              </div>
            </CollapsibleContent>
          </Collapsible>
        ) : null}
      </div>
    </ToolRow>
  );
});
