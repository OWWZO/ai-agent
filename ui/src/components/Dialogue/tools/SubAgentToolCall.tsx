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
 * 同步与后台（run_in_background）共用这一张卡，后台仅多一个「后台」chip。
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

  // 摘要行动态文案：运行中显示最新过程增量/进度行，结束后用 subagent_end 的摘要，
  // 都没有时退回静态 description。ToolRow 内部按 arg 变化做上滚动画，这里只给文案。
  const lastProgressLine =
    sub.progressLines.length > 0
      ? sub.progressLines[sub.progressLines.length - 1]
      : "";
  const summaryText =
    sub.status === "running"
      ? sub.liveText || lastProgressLine || sub.description
      : sub.summary || sub.description;
  /**
   * 同步 / 后台是**同一张卡**（用户口径：异步不要另起一套视觉）。
   * 后台只在摘要行尾部挂一个静态 chip 做区分，运行细节统一走右上角「智能体」面板
   * 与右侧工作区的子智能体 tab —— 不在时间线里再铺一层过程盒子。
   */
  const backgroundChip = sub.runInBackground ? "后台" : "";

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
      separator={summaryText ? "·" : undefined}
      arg={summaryText || ""}
      chip={backgroundChip}
      // 失败原因走 ToolRow 的富提示卡（悬停「执行失败」看原文），不再往时间线里塞错误行
      statusTitle={sub.errorMsg || undefined}
      open={false}
      // ZCode `canToggle={false}`：卡内不可展开，摘要行本身就是打开详情面板的入口。
      expandable={false}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onOpenWorkspace={openDetail}
    />
  );
});
