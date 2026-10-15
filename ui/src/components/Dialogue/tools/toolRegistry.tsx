import type { ReactNode } from "react";
import { isSubAgentParentTask } from "@/utils/chat/subagent";
import { AskUserToolCall } from "./AskUserToolCall";
import DesktopControlCard from "../DesktopControlCard";
import { EditToolCall } from "./EditToolCall";
import {
  GenericToolCall,
  resolveStackPosition,
  aggregateToolStatuses,
} from "./GenericToolCall";
import { McpToolCall } from "./McpToolCall";
import { isMcpToolName } from "./mcpToolName";
import { ReadToolCall } from "./ReadToolCall";
import { SubAgentToolCall } from "./SubAgentToolCall";
import { normalizeToolName } from "./toolMeta";
import type { ToolRowStackPosition } from "./ToolRow";
import { resolveTaskToolName, resolveToolKey } from "./toolTaskAdapter";
import { useToolEntranceAnimation } from "./useToolEntranceAnimation";

export type ToolCallViewProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  stackPosition?: ToolRowStackPosition;
  defaultExpanded?: boolean;
  /**
   * 当前对话是否正在流式输出。为 true 时，首次出现的工具卡会播一次进场淡入
   * （对齐 ZCode `streamingEntranceActive`）；历史回放 / 完成后重挂不播。
   */
  streaming?: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changeFile?: CHAT.OpenFileHandler;
  changePlan?: () => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem, fileIndex?: number) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

export type ToolRendererKind =
  | "edit"
  | "read"
  | "agent"
  | "askuser"
  | "desktop"
  | "mcp"
  | "generic";

/** 对齐 kimi toolRegistry.resolveToolRenderer */
export function resolveToolRendererKind(tool: CHAT.Task): ToolRendererKind {
  const rawName = resolveTaskToolName(tool);
  // MCP 判定必须用**原始**工具名：normalizeToolName 会小写化，破坏 server/tool 段大小写。
  if (isMcpToolName(rawName)) {
    return "mcp";
  }
  const name = normalizeToolName(rawName);
  if (name === "edit" || name === "write" || name === "multi_edit") {
    return "edit";
  }
  // read 单独分流：文件名 chip 要能打开右侧文件内容预览（不是 diff）
  if (name === "read") {
    return "read";
  }
  if (name === "task" || isSubAgentParentTask(tool)) {
    return "agent";
  }
  if (name === "askuserquestion" || tool.messageType === "ask_user_question") {
    return "askuser";
  }
  if (name === "requestdesktopcontrol" || tool.messageType === "desktop_control") {
    return "desktop";
  }
  return "generic";
}

function AskUserAdapter(props: ToolCallViewProps) {
  return (
    <AskUserToolCall
      tool={props.tool}
      stackPosition={props.stackPosition}
      defaultExpanded={props.defaultExpanded}
    />
  );
}

/** 时间线统一入口：按工具名分发 Edit / Read / MCP / Agent / AskUser / Generic */
export function ToolCallView(props: ToolCallViewProps) {
  const kind = resolveToolRendererKind(props.tool);
  // 进场淡入挂在最外层：ZCode 也是包在 `div.w-full` 上，而不是卡片内部。
  // 去重按 toolId，避免历史重放 / 虚拟列表重挂时整屏闪。
  const shouldAnimateEntrance = useToolEntranceAnimation(
    resolveToolKey(props.tool),
    Boolean(props.streaming)
  );

  let content: ReactNode;
  if (kind === "edit") content = <EditToolCall {...props} />;
  else if (kind === "read") content = <ReadToolCall {...props} />;
  else if (kind === "mcp") content = <McpToolCall {...props} />;
  else if (kind === "agent") content = <SubAgentToolCall {...props} />;
  else if (kind === "askuser") content = <AskUserAdapter {...props} />;
  else if (kind === "desktop") content = <DesktopControlCard tool={props.tool} />;
  else content = <GenericToolCall {...props} />;

  return (
    <div
      className="w-full"
      data-zcode-tool-stream-animate={shouldAnimateEntrance ? "true" : undefined}
    >
      {content}
    </div>
  );
}

export { resolveStackPosition, aggregateToolStatuses };
