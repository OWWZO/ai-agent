import { memo, useEffect, useMemo, useState, type ReactNode } from "react";import {
  AppWindowIcon,
  BotIcon,
  FolderIcon,
  GlobeIcon,
  HelpCircleIcon,
  ListTodoIcon,
  PencilIcon,
  SearchIcon,
  SquareTerminalIcon,
  WrenchIcon,
} from "lucide-react";
import { getTaskFiles } from "@/utils/taskArtifacts";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { ToolOutputBlock } from "./ToolOutputBlock";
import { ToolParametersBlock } from "./ToolParametersBlock";
import { ToolArgStreamPreview } from "./ToolArgStreamPreview";
import {
  extractExecuteCommand,
  normalizeToolName,
  toolLabel,
  toolSummary,
} from "./toolMeta";
import {
  resolveTaskToolArg,
  resolveTaskToolName,
  resolveTaskToolOutput,
  resolveTaskToolPatch,
  resolveTaskToolStatus,
  resolveToolKey,
} from "./toolTaskAdapter";
import { useToolCardOpen } from "./useToolCardOpen";
import {
  formatToolStreamChip,
  isToolArgStreaming,
  resolveToolStreamPath,
  shouldShowToolArgStream,
} from "./toolStreamPreview";
import { parseToolJson } from "./ToolJsonBlock";
import { editDiffStats } from "./toolDiff";
import { ToolDiffCount } from "./ToolDiffCount";
import { ExecuteOutput } from "./ExecuteOutput";

/**
 * 工具图标：与 ZCode `ToolCallBlocks/renderers/*` 逐一对齐
 * （read/search/explore 共用放大镜，edit/write 共用铅笔，execute 用方框终端），
 * 尺寸统一 `size-4`（16px），与 ZCode 的 `className="size-4 shrink-0"` 一致。
 */
function toolGlyph(name: string): ReactNode {
  const key = normalizeToolName(name);
  const cls = "size-4";
  switch (key) {
    case "read":
    case "grep":
    case "search":
    case "glob":
      return <SearchIcon className={cls} />;
    case "bash":
      return <SquareTerminalIcon className={cls} />;
    case "edit":
    case "multi_edit":
    case "write":
      return <PencilIcon className={cls} />;
    case "ls":
      return <FolderIcon className={cls} />;
    case "web_fetch":
      return <GlobeIcon className={cls} />;
    case "todo":
      return <ListTodoIcon className={cls} />;
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
 * Edit / Read / Agent / AskUser / MCP 由 toolRegistry 分流，不走进这里。
 *
 * 展开体顺序对齐 ZCode `ToolCallBody.tsx`：Parameters → Result → 原始 JSON 兜底。
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
  stackPosition = "single",
  defaultExpanded,
  changeActiveChat,
  changeFile,
}: GenericToolCallProps) {
  const name = resolveTaskToolName(tool);
  const arg = resolveTaskToolArg(tool);
  const status = resolveTaskToolStatus(tool);
  const output = resolveTaskToolOutput(tool);
  const argStreaming = isToolArgStreaming(tool);
  const showArgStream = shouldShowToolArgStream(tool);
  const streamPath = showArgStream ? resolveToolStreamPath(arg) : "";
  const summary = showArgStream
    ? streamPath || (argStreaming ? "生成参数中…" : toolSummary(name, arg))
    : toolSummary(name, arg);
  const inputJson = parseToolJson(arg);
  // 摘要行已不再挂任何文本 chip（ZCode 的摘要只有图标 + 类别 + 主文本 + diffCount）；
  // 仅入参流预览还保留一个参数统计 chip。
  const chip = showArgStream ? formatToolStreamChip(arg, arg) : "";

  // 变更计数走翻页节点（对齐 ZCode）。Edit/Write 正常由 EditToolCall 接管，
  // 这里只是让 registry 分流变化时同一份 diff 语义不会退化成静态字符串。
  // 优先用后端 unified diff（带真实行号 + 上下文），缺失时 toolDiff 自己回退 LCS。
  const backendPatch = useMemo(() => resolveTaskToolPatch(tool), [tool]);
  const diffStats = showArgStream
    ? null
    : editDiffStats(name, arg, backendPatch);
  const chipNode = diffStats ? (
    <ToolDiffCount added={diffStats.added} removed={diffStats.removed} />
  ) : undefined;

  const isRunningBash =
    status === "running" && normalizeToolName(name) === "bash";
  const hasOutput = output.length > 0;
  /** execute/bash 卡：展开区走 `$ 命令` + 吸底输出（对齐 ZCode execute.tsx）。 */
  const isExecute = normalizeToolName(name) === "bash";
  const executeCommand = isExecute ? extractExecuteCommand(arg) : "";
  const isError = status === "error";

  /**
   * 失败态只保留可读错误文本：ZCode `ToolOutput` 在 `errorText` 存在时**不渲染**结构化输出，
   * `showInput` 也会被策略层强制关掉 —— 否则 `old_string`/`new_string` 整坨 JSON 会把报错顶下去。
   * Reactor 的失败原因就藏在 output 里，所以这里直接把它当 errorText 交给结果段。
   */
  const errorText = isError ? output.join("\n").trim() : "";
  const hasParameters = !isError && Boolean(inputJson || arg.trim());

  /**
   * 原始 JSON 兜底（对齐 ZCode `renderers/fallback.tsx`）：
   * 只有在**这张卡既没有结构化入参、也没有任何输出**时才铺原始数据。
   * ZCode 的判据是「有没有 inlinePreview」—— 那边 Glob/Grep/WebFetch 都有专用 renderer
   * 不会走到 fallback；Reactor 这些工具共用本组件，若照抄字面判据会给每张搜索卡
   * 都挂一份原始 JSON 转储，所以这里按「卡片是否为空」等价折算。
   */
  const showRawFallback = !hasParameters && !hasOutput && !isExecute;
  const rawFallbackPayload = {
    input: arg,
    resultMap: tool.resultMap,
  };
  const rawFallbackJson = showRawFallback
    ? JSON.stringify(rawFallbackPayload, null, 2)
    : "";

  const canExpand =
    hasParameters ||
    hasOutput ||
    Boolean(errorText) ||
    isRunningBash ||
    showRawFallback ||
    showArgStream;

  /**
   * 默认折叠：种子只认 `defaultExpanded`，**不再把 `showArgStream` 也算进去**。
   * 入参流分支在上面提前 return 了自己的预览面板，根本不看 `open`；旧逻辑却把它
   * 种成 `open=true` 并写进持久表，于是参数流一结束卡片就带着入参/出参摊开，
   * 而且再也不会自己收起来（时间线此前还额外传了 `defaultExpanded={step.active}`）。
   */
  const [open, setOpen] = useToolCardOpen(
    resolveToolKey(tool),
    Boolean(defaultExpanded)
  );
  const [userToggled, setUserToggled] = useState(false);

  useEffect(() => {
    // 用户手动开合过就不再自动展开，避免把用户的操作顶掉。
    if (userToggled) {
      return;
    }
    if (defaultExpanded && canExpand) {
      setOpen(true);
    }
  }, [canExpand, defaultExpanded, userToggled, setOpen]);

  const stacked = stackPosition !== "single";
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
      // ZCode 的失败状态词带 hover 提示展示报错原文；这里用富提示卡承载同一份文本。
      statusTitle={isError && output.length > 0 ? output.join("\n") : undefined}
      // 运行期间摘要文案随入参流式刷新，走上滚而不是硬切（对齐 ZCode animateSummaryContent）。
      animateSummary={status === "running"}
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
              className={isError ? "is-error" : undefined}
            />
          ) : (
            // ZCode execute 的无输出态：一行等宽次浅色文本，**不套卡片**。
            <p className="kimi-tool-execute-no-output">暂无输出</p>
          )}
        </div>
      ) : (
        <>
          {hasParameters ? (
            <ToolParametersBlock
              value={inputJson}
              text={inputJson ? undefined : arg}
            />
          ) : null}
          {showRawFallback ? (
            <pre className="kimi-tool-raw-json" data-testid="tool-raw-json">
              {rawFallbackJson}
            </pre>
          ) : null}
          <ToolOutputBlock
            lines={output}
            errorText={errorText || undefined}
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
