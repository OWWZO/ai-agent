import { memo, useEffect, useMemo, useState } from "react";
import {
  FilePenLineIcon,
  FilePlusIcon,
} from "lucide-react";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { ToolFileChip } from "./ToolFileChip";
import { ToolOutputBlock } from "./ToolOutputBlock";
import { ToolArgStreamPreview } from "./ToolArgStreamPreview";
import {
  editDiffStats,
  extractEditFileSummaries,
  formatDiffStats,
  summarizeEditFiles,
} from "./toolDiff";
import { ToolDiffCount } from "./ToolDiffCount";
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
import { ToolJsonBlock, parseToolJson } from "./ToolJsonBlock";

/**
 * 打开右侧预览的载荷：具体到「哪一个文件」。
 * 多文件 edit 拆成子卡后，每张子卡各带自己的 patch，不能只传 task。
 */
export type ToolPreviewRequest = {
  task: CHAT.Task;
  chat: CHAT.ChatItem;
  /** 多文件 edit 的文件序号（单文件不传） */
  fileIndex?: number;
};

type EditToolCallProps = {
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

export const EditToolCall = memo(function EditToolCall({
  tool,
  chat,
  durationMs,
  durationLabel,
  stackPosition = "single",
  defaultExpanded,
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
  const inputJson = parseToolJson(arg);
  // 只算一次 diff 统计：chip 文案与翻页数字节点共用同一份结果，
  // 避免 formatEditDiffChip 内部再跑一遍 O(n*m) 的 LCS。
  const diffStats =
    showArgStream || status === "error" ? null : editDiffStats(name, arg);

  // 多文件 edit：逐文件拆摘要。单文件时数组长度为 1，走同一条渲染路径。
  const fileSummaries = useMemo(
    () =>
      showArgStream || status === "error"
        ? []
        : extractEditFileSummaries(name, arg),
    [name, arg, showArgStream, status]
  );
  const isMultiFile = fileSummaries.length > 1;
  const totalStats = useMemo(
    () => (isMultiFile ? summarizeEditFiles(fileSummaries) : null),
    [isMultiFile, fileSummaries]
  );

  const chip = showArgStream
    ? formatToolStreamChip(arg, arg)
    : status === "error"
      ? ""
      : diffStats
        ? formatDiffStats(diffStats)
        : "edited";
  const chipNode = diffStats ? (
    <ToolDiffCount added={diffStats.added} removed={diffStats.removed} />
  ) : undefined;
  const hasOutput = output.length > 0;
  const canExpandInline = hasOutput || Boolean(summaryFull) || Boolean(inputJson);
  const expandable = showArgStream || canExpandInline;

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

  // 多文件 edit：对齐 ZCode —— 主体一行总览（N 个文件 + 总 diff），
  // 下面每个文件一张独立子卡（各有自己的文件名 chip 与 diff 统计）。
  if (isMultiFile) {
    return (
      <div className="kimi-tool-multi-edit" data-testid="edit-tool-multi-file">
        <ToolRow
          status={status}
          icon={icon}
          name={toolLabel(name)}
          arg={!open ? summary : ""}
          time={timing}
          chipNode={
            totalStats ? (
              <ToolDiffCount added={totalStats.added} removed={totalStats.removed} />
            ) : undefined
          }
          chip={totalStats ? undefined : chip}
          open={open}
          expandable={expandable}
          stacked={stackPosition !== "single"}
          stackPosition={stackPosition}
          onToggle={handleToggle}
        >
          <div className="kimi-tool-row-summary">
            <div className="mb-1 text-ui-xs uppercase tracking-wide text-[var(--color-text-faint)]">
              共 {fileSummaries.length} 个文件
            </div>
            {inputJson ? <ToolJsonBlock data={inputJson} /> : summaryFull}
          </div>
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
        <div className="kimi-tool-multi-edit-list">
          {fileSummaries.map((file, index) => (
            <EditFileSubCard
              key={`${file.path}:${index}`}
              file={file}
              status={status}
              timing={index === 0 ? timing : ""}
              onOpen={
                onOpenToolDiff
                  ? () => onOpenToolDiff(tool, chat, index)
                  : undefined
              }
            />
          ))}
        </div>
      </div>
    );
  }

  const singlePath = fileSummaries[0]?.path;
  const singleOpen = onOpenToolDiff
    ? () => onOpenToolDiff(tool, chat)
    : undefined;

  return (
    <ToolRow
      status={status}
      icon={icon}
      name={toolLabel(name)}
      arg={!open ? summary : ""}
      argNode={
        singlePath ? <ToolFileChip path={singlePath} onOpen={singleOpen} /> : undefined
      }
      time={timing}
      chip={chip}
      chipNode={chipNode}
      open={open}
      expandable={expandable}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onToggle={handleToggle}
    >
      {summaryFull || inputJson ? (
        <div className="kimi-tool-row-summary">
          {inputJson ? <ToolJsonBlock data={inputJson} /> : summaryFull}
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
    </ToolRow>
  );
});

/** 多文件 edit 的单个文件子卡：只有摘要行，不带展开区（对齐 ZCode `EditFileSummaryBlock`）。 */
const EditFileSubCard = memo(function EditFileSubCard({
  file,
  status,
  timing,
  onOpen,
}: {
  file: { path: string; fileName: string; added: number; removed: number };
  status: "running" | "ok" | "error";
  timing: string;
  onOpen?: () => void;
}) {
  return (
    <ToolRow
      status={status}
      name=""
      arg=""
      argNode={<ToolFileChip path={file.path} onOpen={onOpen} />}
      time={timing}
      chipNode={<ToolDiffCount added={file.added} removed={file.removed} />}
      stacked
      stackPosition="single"
    />
  );
});
