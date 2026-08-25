import { memo, useEffect, useState } from "react";
import {
  FilePenLineIcon,
  FilePlusIcon,
} from "lucide-react";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { ToolOutputBlock } from "./ToolOutputBlock";
import { ToolArgStreamPreview } from "./ToolArgStreamPreview";
import { formatEditDiffChip, prefersToolDiffPanel } from "./toolDiff";
import { normalizeToolName, toolLabel, toolSummary } from "./toolMeta";
import {
  formatDurationLabel,
  resolveTaskToolArg,
  resolveTaskToolName,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
} from "./toolTaskAdapter";
import {
  formatToolStreamChip,
  isToolArgStreaming,
  resolveToolStreamPath,
  shouldShowToolArgStream,
} from "./toolStreamPreview";

type EditToolCallProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  durationMs?: number;
  durationLabel?: string;
  stackPosition?: ToolRowStackPosition;
  defaultExpanded?: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

export const EditToolCall = memo(function EditToolCall({
  tool,
  chat,
  durationMs,
  durationLabel,
  stackPosition = "single",
  defaultExpanded,
  changeActiveChat,
  onOpenToolDiff,
}: EditToolCallProps) {
  const name = resolveTaskToolName(tool);
  const arg = resolveTaskToolArg(tool);
  const status = resolveTaskToolStatus(tool);
  const output = resolveTaskToolOutput(tool);
  const timing = durationLabel || formatDurationLabel(durationMs);
  const argStreaming = isToolArgStreaming(tool);
  const showArgStream = shouldShowToolArgStream(tool);
  const streamPath = showArgStream ? resolveToolStreamPath(arg) : "";
  const summary = showArgStream
    ? streamPath || (argStreaming ? "生成参数中…" : toolSummary(name, arg))
    : toolSummary(name, arg);
  const summaryFull = toolSummary(name, arg, true);
  const chip = showArgStream
    ? formatToolStreamChip(arg, arg)
    : status === "error"
      ? ""
      : formatEditDiffChip(name, arg) || "edited";
  const useDiffPanel =
    !showArgStream && Boolean(onOpenToolDiff) && prefersToolDiffPanel(name);
  const hasOutput = output.length > 0;
  const canExpandInline =
    (hasOutput && !useDiffPanel) || Boolean(summaryFull && !useDiffPanel);
  const expandable = showArgStream ? true : canExpandInline || useDiffPanel;

  const [open, setOpen] = useState(
    () => Boolean(defaultExpanded) || showArgStream
  );
  const [userToggled, setUserToggled] = useState(false);

  useEffect(() => {
    if (userToggled && !showArgStream) {
      return;
    }
    if (showArgStream) {
      setOpen(true);
      return;
    }
    if (defaultExpanded && canExpandInline) {
      setOpen(true);
    }
  }, [showArgStream, canExpandInline, defaultExpanded, userToggled]);

  const kind = normalizeToolName(name);
  const icon =
    kind === "write" ? (
      <FilePlusIcon className="size-3.5" />
    ) : (
      <FilePenLineIcon className="size-3.5" />
    );

  const handleToggle = () => {
    if (useDiffPanel) {
      onOpenToolDiff?.(tool, chat);
      return;
    }
    if (canExpandInline) {
      setUserToggled(true);
      setOpen((v) => !v);
    }
  };

  // write/edit 入参流：与终答同款；running 时仍展示完整 args（解耦 status）
  if (showArgStream) {
    return (
      <div className="kimi-tool-arg-stream" data-testid="edit-tool-arg-stream">
        <div className="kimi-tool-arg-stream-head">
          <span className="kimi-tool-arg-stream-glyph">{icon}</span>
          <span className="kimi-tool-arg-stream-name">{toolLabel(name)}</span>
          {summary ? (
            <span className="kimi-tool-arg-stream-meta" title={summary}>
              {summary}
            </span>
          ) : null}
          {chip ? (
            <span className="kimi-tool-arg-stream-chip">{chip}</span>
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
      icon={icon}
      name={toolLabel(name)}
      arg={!open ? summary : ""}
      time={timing}
      chip={chip}
      open={open}
      expandable={expandable}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onToggle={handleToggle}
      onOpenWorkspace={() => changeActiveChat(tool, chat)}
    >
      {summaryFull ? (
        <div className="kimi-tool-row-summary">{summaryFull}</div>
      ) : null}
      <ToolOutputBlock
        lines={output}
        tone={status === "error" ? "error" : "default"}
        emptyText={
          status === "error"
            ? "工具执行失败"
            : status === "running"
              ? "等待输出…"
              : "暂无输出"
        }
      />
    </ToolRow>
  );
});
