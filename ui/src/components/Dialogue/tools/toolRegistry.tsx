import { isAgentDispatchTask } from "@/utils/chat/subagent";
import { AskUserToolCall } from "./AskUserToolCall";
import { EditToolCall } from "./EditToolCall";
import {
  GenericToolCall,
  resolveStackPosition,
  aggregateToolStatuses,
} from "./GenericToolCall";
import { SubAgentToolCall } from "./SubAgentToolCall";
import { normalizeToolName } from "./toolMeta";
import type { ToolRowStackPosition } from "./ToolRow";
import { resolveTaskToolName } from "./toolTaskAdapter";

export type ToolCallViewProps = {
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

export type ToolRendererKind = "edit" | "agent" | "askuser" | "generic";

/** 对齐 kimi toolRegistry.resolveToolRenderer */
export function resolveToolRendererKind(tool: CHAT.Task): ToolRendererKind {
  const name = normalizeToolName(resolveTaskToolName(tool));
  if (name === "edit" || name === "write" || name === "multi_edit") {
    return "edit";
  }
  if (name === "task" || isAgentDispatchTask(tool)) {
    return "agent";
  }
  if (name === "askuserquestion" || tool.messageType === "ask_user_question") {
    return "askuser";
  }
  return "generic";
}

function AskUserAdapter(props: ToolCallViewProps) {
  return (
    <AskUserToolCall
      tool={props.tool}
      durationMs={props.durationMs}
      durationLabel={props.durationLabel}
      stackPosition={props.stackPosition}
      defaultExpanded={props.defaultExpanded}
    />
  );
}

/** 时间线统一入口：按工具名分发 Edit / Agent / AskUser / Generic */
export function ToolCallView(props: ToolCallViewProps) {
  const kind = resolveToolRendererKind(props.tool);
  if (kind === "edit") return <EditToolCall {...props} />;
  if (kind === "agent") return <SubAgentToolCall {...props} />;
  if (kind === "askuser") return <AskUserAdapter {...props} />;
  return <GenericToolCall {...props} />;
}

export { resolveStackPosition, aggregateToolStatuses };
