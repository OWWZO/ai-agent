import { memo, useMemo } from "react";
import { BotIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { resolveSubagentColorFromName } from "@/lib/subagentColors";
import { resolveSubAgentDisplay } from "@/utils/chat/subagent";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import { toolLabel } from "./toolMeta";
import { resolveTaskToolStatus } from "./toolTaskAdapter";

type SubAgentToolCallProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  stackPosition?: ToolRowStackPosition;
  defaultExpanded?: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem, fileIndex?: number) => void;
};

/**
 * 子智能体卡（对齐 ZCode `ToolCallBlocks/renderers/agent.tsx`）。
 *
 * ZCode 在这里是 `canToggle={false}` + 整行可点：**卡内永远不展开**，只保留一行摘要，
 * 完整过程统一从右侧详情面板看。源码注释把这条写成了产品边界：
 * 「Agent/Task 在父对话中只保留单行摘要；完整 child timeline 统一从右侧 tab / 手机抽屉查看，
 * 因此这里既不自动展开，也不提供手动展开入口。」
 *
 * 所以这里没有入参段也没有结果段 —— 子代理的 prompt 与输出都归 `AgentDetailPanel`。
 */
export const SubAgentToolCall = memo(function SubAgentToolCall({
  tool,
  chat,
  stackPosition = "single",
  changeActiveChat,
  onOpenAgent,
}: SubAgentToolCallProps) {
  const sub = useMemo(() => resolveSubAgentDisplay(tool), [tool]);

  const status =
    sub.status === "failed"
      ? "error"
      : sub.status === "running"
        ? "running"
        : resolveTaskToolStatus(tool);

  const agentName = sub.subagentType || "";
  const agentColor = agentName ? resolveSubagentColorFromName(agentName) : null;

  const openDetail = () => {
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
      icon={<BotIcon className="size-4" />}
      name={toolLabel("task")}
      kindDetail={
        agentName && agentColor ? (
          <span
            className={cn("kimi-agent-name", `is-${agentColor}`)}
            title={agentName}
          >
            {agentName}
          </span>
        ) : undefined
      }
      separator={sub.description ? "·" : undefined}
      arg={sub.description || ""}
      open={false}
      // ZCode `canToggle={false}`：卡内不可展开，摘要行本身就是打开详情面板的入口。
      expandable={false}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onOpenWorkspace={openDetail}
    />
  );
});
