import { memo, useEffect, useState, type ReactNode } from "react";
import {
  AppWindowIcon,
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
import { getTaskFiles } from "@/utils/taskArtifacts";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { ToolOutputBlock } from "./ToolOutputBlock";
import { ToolArgStreamPreview } from "./ToolArgStreamPreview";
import {
  extractExecuteCommand,
  normalizeToolName,
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
import { ToolJsonBlock, parseToolJson } from "./ToolJsonBlock";
import { editDiffStats } from "./toolDiff";
import { ToolDiffCount } from "./ToolDiffCount";
import { ExecuteOutput } from "./ExecuteOutput";

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
    case "canvas_publish":
    case "html":
      return <AppWindowIcon className={cls} />;
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
  changeFile?: CHAT.OpenFileHandler;
  changePlan?: () => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem, fileIndex?: number) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

/**
 * 默认工具卡（对齐 kimi GenericTool）。
 * Edit / Agent / AskUser 由 toolRegistry 分流，不走进这里。
 */
function shouldOpenCanvasPreview(tool: CHAT.Task): boolean {
  const name = normalizeToolName(resolveTaskToolName(tool));
  if (name !== "canvas_publish" && name !== "html" && tool.messageType !== "html") {
    return false;
  }
  return getTaskFiles(tool).length > 0;
}

export const GenericToolCall = memo(function GenericToolCall({
  tool,
  chat,
  durationMs,
  durationLabel,
  stackPosition = "single",
  defaultExpanded,
  changeActiveChat,
  changeFile,
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
  const inputJson = parseToolJson(arg);
  const chip = showArgStream
    ? formatToolStreamChip(arg, arg)
    : toolChip({
      name,
      arg,
      output,
      timing,
      status,
    });

  // 变更计数走翻页节点（对齐 ZCode）。Edit/Write 正常由 EditToolCall 接管，
  // 这里只是让 registry 分流变化时同一份 diff 语义不会退化成静态字符串。
  const diffStats = showArgStream ? null : editDiffStats(name, arg);
  const chipNode = diffStats ? (
    <ToolDiffCount added={diffStats.added} removed={diffStats.removed} />
  ) : undefined;

  const isRunningBash =
    status === "running" && normalizeToolName(name) === "bash";
  const hasOutput = output.length > 0;
  /** execute/bash 卡：展开区走 `$ 命令` + 吸底输出（对齐 ZCode execute.tsx）。 */
  const isExecute = normalizeToolName(name) === "bash";
  const executeCommand = isExecute ? extractExecuteCommand(arg) : "";

  /**
   * fallback（对齐 ZCode `renderers/fallback.tsx`）：既没有结构化入参可预览、
   * 也没有任何输出时，展开区会完全空白 —— 那种情况退化为整块原始 JSON，
   * 至少让用户能自查工具到底带了什么字段。
   */
  const showRawFallback = !inputJson && !summaryFull && !hasOutput && !isExecute;
  const rawFallback = showRawFallback
    ? {
      input: arg,
      resultMap: tool.resultMap
    }
    : null;

  const canExpand =
    hasOutput ||
    isRunningBash ||
    Boolean(summaryFull) ||
    Boolean(inputJson) ||
    showRawFallback ||
    showArgStream;

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

  // 工具卡片默认内联展开参数和出参；文件附件等独立入口再打开工作区。
  const expandable = showArgStream || canExpand;

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
      icon={toolGlyph(name)}
      name={toolLabel(name)}
      arg={!open ? summary : ""}
      time={normalizeToolName(name) !== "bash" ? timing : ""}
      chip={chip}
      chipNode={chipNode}
      open={open}
      expandable={expandable}
      stacked={stacked}
      stackPosition={stackPosition}
      onToggle={() => {
        setUserToggled(true);
        setOpen((v) => !v);
      }}
      onOpenWorkspace={
        shouldOpenCanvasPreview(tool)
          ? () => {
              const files = getTaskFiles(tool);
              if (files.length && changeFile) {
                changeFile(files[0], chat);
                return;
              }
              changeActiveChat(tool, chat);
            }
          : undefined
      }
    >
      {isExecute ? (
        // execute：展开区 = 等宽完整命令 + 吸底输出。
        // 命令用 `$` 前缀保持终端观感；输出交给 ExecuteOutput 做程序吸底 / 上滚冻结。
        <div className="kimi-tool-execute">
          {executeCommand ? (
            <div className="kimi-tool-execute-command">
              <span className="kimi-tool-execute-prompt">$</span>
              <pre className="kimi-tool-execute-script">{executeCommand}</pre>
            </div>
          ) : null}
          {output.length > 0 || isRunningBash ? (
            <ExecuteOutput
              text={output.join("\n")}
              running={isRunningBash}
              className={status === "error" ? "is-error" : undefined}
            />
          ) : (
            <div className="kimi-tool-output">
              <div className="kimi-tool-output-empty">
                {status === "error" ? "工具执行失败" : "暂无输出"}
              </div>
            </div>
          )}
        </div>
      ) : (
        <>
          {summaryFull || inputJson ? (
            <div className="kimi-tool-row-summary">
              <div className="mb-1 text-ui-xs uppercase tracking-wide text-[var(--color-text-faint)]">
                参数
              </div>
              {inputJson ? <ToolJsonBlock data={inputJson} /> : summaryFull}
            </div>
          ) : null}
          {rawFallback ? (
            <div className="kimi-tool-row-summary">
              <div className="mb-1 text-ui-xs uppercase tracking-wide text-[var(--color-text-faint)]">
                原始数据
              </div>
              <ToolJsonBlock data={rawFallback} />
            </div>
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
        </>
      )}
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
