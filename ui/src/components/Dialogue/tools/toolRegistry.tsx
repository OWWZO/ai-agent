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
import { resolveTaskToolName } from "./toolTaskAdapter";

export type ToolCallViewProps = {
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
  if (kind === "edit") return <EditToolCall {...props} />;
  if (kind === "read") return <ReadToolCall {...props} />;
  if (kind === "mcp") return <McpToolCall {...props} />;
  if (kind === "agent") return <SubAgentToolCall {...props} />;
  if (kind === "askuser") return <AskUserAdapter {...props} />;
  if (kind === "desktop") return <DesktopControlCard tool={props.tool} />;
  return <GenericToolCall {...props} />;
}

export { resolveStackPosition, aggregateToolStatuses };
