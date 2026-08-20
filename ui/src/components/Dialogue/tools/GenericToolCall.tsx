import { memo, useEffect, useState, type ReactNode } from "react";
import {
  BotIcon,
  FilePenLineIcon,
  FilePlusIcon,
  FileTextIcon,
  FolderIcon,
  GlobeIcon,
  HelpCircleIcon,
  SearchIcon,
  SparklesIcon,
  TerminalIcon,
  WrenchIcon,
} from "lucide-react";
import { canOpenTaskWorkspacePanel } from "@/components/ChatView/streamState";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { ToolOutputBlock } from "./ToolOutputBlock";
import { ToolArgStreamPreview } from "./ToolArgStreamPreview";
import {
  normalizeToolName,
  opensWorkspacePreferentially,
  toolChip,
  toolLabel,
  toolSummary,
} from "./toolMeta";
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

function toolGlyph(name: string): ReactNode {
  const key = normalizeToolName(name);
  const cls = "size-3.5";
  switch (key) {
    case "read":
      return <FileTextIcon className={cls} />;
    case "bash":
      return <TerminalIcon className={cls} />;
    case "edit":
    case "multi_edit":
      return <FilePenLineIcon className={cls} />;
    case "write":
      return <FilePlusIcon className={cls} />;
    case "grep":
    case "search":
      return <SearchIcon className={cls} />;
    case "glob":
      return <SearchIcon className={cls} />;
    case "ls":
      return <FolderIcon className={cls} />;
    case "web_fetch":
      return <GlobeIcon className={cls} />;
    case "todo":
      return <SparklesIcon className={cls} />;
    case "task":
      return <BotIcon className={cls} />;
    case "askuserquestion":
      return <HelpCircleIcon className={cls} />;
    default:
      return <WrenchIcon className={cls} />;
  }
}

type GenericToolCallProps = {
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

/**
 * 默认工具卡（对齐 kimi GenericTool）。
 * Edit / Agent / AskUser 由 toolRegistry 分流，不走进这里。
 */
export const GenericToolCall = memo(function GenericToolCall({
  tool,
  chat,
  durationMs,
  durationLabel,
  stackPosition = "single",
  defaultExpanded,
  changeActiveChat,
  changePlan,
}: GenericToolCallProps) {
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
    : toolChip({
        name,
        arg,
        output,
        timing,
        status,
      });

  const isRunningBash =
    status === "running" && normalizeToolName(name) === "bash";
  const hasOutput = output.length > 0;
  const canExpand =
    hasOutput || isRunningBash || Boolean(summaryFull) || showArgStream;
  const preferWorkspace =
    !showArgStream &&
    opensWorkspacePreferentially(name) &&
    canOpenTaskWorkspacePanel(tool);

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
    if (defaultExpanded && canExpand) {
      setOpen(true);
    }
  }, [showArgStream, canExpand, defaultExpanded, userToggled]);

  const stacked = stackPosition !== "single";

  const openWorkspace = () => {
    if (tool.messageType === "plan") {
      changePlan?.();
      return;
    }
    if (canOpenTaskWorkspacePanel(tool)) {
      changeActiveChat(tool, chat);
    }
  };

  // 入参流期间强制可展开，且不走「只开工作区」
  const expandable = showArgStream ? true : preferWorkspace ? false : canExpand;

  // 入参流：像终答一样直接铺在工具名下方（不依赖折叠 body / status）
  if (showArgStream) {
    return (
      <div
        className="kimi-tool-arg-stream"
        data-testid="generic-tool-arg-stream"
      >
        <div className="kimi-tool-arg-stream-head">
          <span className="kimi-tool-arg-stream-glyph">{toolGlyph(name)}</span>
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
          label="Arguments"
          streaming={argStreaming}
          prominent
        />
      </div>
    );
  }

  return (
    <ToolRow
      status={status}
      icon={toolGlyph(name)}
      name={toolLabel(name)}
      arg={!open ? summary : ""}
      time={normalizeToolName(name) !== "bash" ? timing : ""}
      chip={chip}
      open={open}
      expandable={expandable}
      stacked={stacked}
      stackPosition={stackPosition}
      onToggle={() => {
        setUserToggled(true);
        setOpen((v) => !v);
      }}
      onOpenWorkspace={openWorkspace}
    >
      {summaryFull ? (
        <div className="kimi-tool-row-summary">
          <div className="mb-1 text-[10px] uppercase tracking-wide text-[var(--color-text-faint)]">
            Parameters
          </div>
          {summaryFull}
        </div>
      ) : null}
      <ToolOutputBlock
        lines={output}
        tone={status === "error" ? "error" : "default"}
        emptyText={
          status === "error"
            ? "工具执行失败"
            : status === "running"
              ? "Waiting for output…"
              : "No output"
        }
      />
    </ToolRow>
  );
});

export function resolveStackPosition(
  index: number,
  total: number
): ToolRowStackPosition {
  if (total <= 1) return "single";
  if (index === 0) return "first";
  if (index === total - 1) return "last";
  return "middle";
}

export function aggregateToolStatuses(
  tools: CHAT.Task[]
): "running" | "error" | "done" {
  if (tools.some((t) => resolveTaskToolStatus(t) === "running")) {
    return "running";
  }
  if (tools.some((t) => resolveTaskToolStatus(t) === "error")) {
    return "error";
  }
  return "done";
}
