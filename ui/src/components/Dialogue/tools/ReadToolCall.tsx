import { memo, useEffect, useState } from "react";
import { FileTextIcon } from "lucide-react";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { ToolFileChip } from "./ToolFileChip";
import { ToolOutputBlock } from "./ToolOutputBlock";
import { ToolArgStreamPreview } from "./ToolArgStreamPreview";
import { extractToolFilePath, normalizeToolName, toolLabel, toolSummary } from "./toolMeta";
import {
  formatDurationLabel,
  resolveTaskToolArg,
  resolveTaskToolName,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
} from "./toolTaskAdapter";
import { isToolArgStreaming, shouldShowToolArgStream } from "./toolStreamPreview";
import { ToolJsonBlock, parseToolJson } from "./ToolJsonBlock";

type ReadToolCallProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  durationMs?: number;
  durationLabel?: string;
  stackPosition?: ToolRowStackPosition;
  defaultExpanded?: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem, fileIndex?: number) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

/**
 * Read 工具卡（对齐 ZCode `ToolCallBlocks/renderers/read.tsx`）。
 *
 * 与 Generic 卡的唯一区别：文件名 chip 可点击 → 右侧打开文件内容
 * （带行号 / 行选择 / 行内评论），而不是内联展开。
 * `canToggle` 与 ZCode 保持一致：读文件的结果太长，默认不提供内联展开，
 * 正文只看文件名，内容交给右侧预览面板。
 */
export const ReadToolCall = memo(function ReadToolCall({
  tool,
  chat,
  durationMs,
  durationLabel,
  stackPosition = "single",
  defaultExpanded,
  onOpenToolDiff,
}: ReadToolCallProps) {
  const name = resolveTaskToolName(tool);
  const arg = resolveTaskToolArg(tool);
  const status = resolveTaskToolStatus(tool);
  const output = resolveTaskToolOutput(tool);
  const timing = durationLabel || formatDurationLabel(durationMs);
  const argStreaming = isToolArgStreaming(tool);
  const showArgStream = shouldShowToolArgStream(tool);
  const summary = toolSummary(name, arg);
  const summaryFull = toolSummary(name, arg, true);
  const inputJson = parseToolJson(arg);
  const filePath = extractToolFilePath(arg);
  const chip = output.length > 0 ? `${output.length} lines` : "";
  const isError = status === "error";

  // ZCode read 渲染器 `canToggle={false}`：出错时才允许展开看原始输出，
  // 正常读取的内容一律走右侧面板，不在消息流里堆正文。
  const expandable = showArgStream || isError;
  const [open, setOpen] = useState(() => Boolean(defaultExpanded) && expandable);

  useEffect(() => {
    if (defaultExpanded && expandable) {
      setOpen(true);
    }
  }, [defaultExpanded, expandable]);

  if (showArgStream) {
    return (
      <div className="kimi-tool-arg-stream" data-testid="read-tool-arg-stream">
        <div className="kimi-tool-arg-stream-head">
          <span className="kimi-tool-arg-stream-glyph">
            <FileTextIcon className="size-3.5" />
          </span>
          <span className="kimi-tool-arg-stream-name">{toolLabel(name)}</span>
          {summary ? (
            <span className="kimi-tool-arg-stream-meta" title={summary}>
              {summary}
            </span>
          ) : null}
        </div>
        <ToolArgStreamPreview
          text={arg}
          label="参数"
          streaming={argStreaming}
          prominent
        />
      </div>
    );
  }

  return (
    <ToolRow
      status={status}
      icon={<FileTextIcon className="size-3.5" />}
      name={toolLabel(name)}
      arg={!open ? summary : ""}
      argNode={
        filePath ? (
          <ToolFileChip
            path={filePath}
            onOpen={
              onOpenToolDiff ? () => onOpenToolDiff(tool, chat) : undefined
            }
          />
        ) : undefined
      }
      time={timing}
      chip={chip}
      open={open}
      expandable={expandable}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onToggle={() => {
        if (expandable) setOpen((v) => !v);
      }}
    >
      {summaryFull || inputJson ? (
        <div className="kimi-tool-row-summary">
          {inputJson ? <ToolJsonBlock data={inputJson} /> : summaryFull}
        </div>
      ) : null}
      <ToolOutputBlock
        lines={output}
        tone={isError ? "error" : "default"}
        emptyText={
          isError
            ? "读取失败"
            : status === "running"
              ? "读取中…"
              : "暂无输出"
        }
      />
    </ToolRow>
  );
});

/** 供 registry 判断是否需要走 read 专用卡（避免 read 名被别名吞掉）。 */
export function isReadTool(name: string): boolean {
  return normalizeToolName(name) === "read";
}
