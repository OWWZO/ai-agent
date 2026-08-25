import { memo, useEffect, useMemo, useState, type MouseEvent } from "react";
import { BotIcon } from "lucide-react";
import {
  formatSubAgentDuration,
  resolveSubAgentDisplay,
} from "@/utils/chat/subagent";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { ToolOutputBlock } from "./ToolOutputBlock";
import { toolLabel } from "./toolMeta";
import {
  formatDurationLabel,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
} from "./toolTaskAdapter";

type SubAgentToolCallProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  durationMs?: number;
  durationLabel?: string;
  stackPosition?: ToolRowStackPosition;
  defaultExpanded?: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

/**
 * 对齐 kimi AgentTool：
 * - 行头：Agent + description
 * - 可展开：subagentType / prompt / 终态输出
 * - 尾部 Open → 右侧详情面板（直播进度）
 */
export const SubAgentToolCall = memo(function SubAgentToolCall({
  tool,
  chat,
  durationMs,
  durationLabel,
  stackPosition = "single",
  defaultExpanded,
  changeActiveChat,
  onOpenAgent,
}: SubAgentToolCallProps) {
  const sub = useMemo(() => resolveSubAgentDisplay(tool), [tool]);
  const nested = tool.children || [];
  const output = useMemo(() => {
    if (sub.content) {
      return sub.content.split("\n");
    }
    return resolveTaskToolOutput(tool);
  }, [sub.content, tool]);

  const status =
    sub.status === "failed"
      ? "error"
      : sub.status === "running"
        ? "running"
        : resolveTaskToolStatus(tool);
  const timing =
    durationLabel ||
    formatSubAgentDuration(sub.totalDurationMs) ||
    formatDurationLabel(durationMs);
  const chip =
    nested.length > 0
      ? `${nested.length} 个工具`
      : sub.totalToolUseCount != null
        ? `${sub.totalToolUseCount} 个工具`
        : "";

  const canExpand = Boolean(
    sub.prompt || sub.subagentType || output.length > 0
  );
  const [open, setOpen] = useState(
    () => Boolean(defaultExpanded) && canExpand
  );

  useEffect(() => {
    if (defaultExpanded && canExpand) {
      setOpen(true);
    }
  }, [defaultExpanded, canExpand]);

  const openDetail = (event?: MouseEvent) => {
    event?.stopPropagation();
    if (onOpenAgent) {
      onOpenAgent(tool, chat);
      return;
    }
    changeActiveChat(tool, chat);
  };

  return (
    <ToolRow
      status={
        status === "ok" || status === "error" || status === "running"
          ? status
          : "ok"
      }
      icon={<BotIcon className="size-3.5" />}
      name={toolLabel("task")}
      arg={!open ? sub.description || sub.subagentType : ""}
      time={timing}
      chip={chip}
      open={open}
      expandable={canExpand}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onToggle={() => {
        if (canExpand) setOpen((v) => !v);
      }}
      trailing={
        <button
          type="button"
          className="kimi-agent-open-btn"
          onClick={openDetail}
        >
          查看
        </button>
      }
    >
      {sub.subagentType ? (
        <div className="mb-1 font-mono text-[12px] text-[var(--color-text-muted)]">
          {sub.subagentType}
        </div>
      ) : null}
      {sub.prompt ? (
        <div className="kimi-tool-row-summary whitespace-pre-wrap">
          {sub.prompt}
        </div>
      ) : null}
      {output.length > 0 ? (
        <ToolOutputBlock
          lines={output}
          tone={status === "error" ? "error" : "default"}
        />
      ) : null}
    </ToolRow>
  );
});
