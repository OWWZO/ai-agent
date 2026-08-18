import { FC, memo, useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import AttachmentList from "@/components/AttachmentList";
import LoadingSpinner from "@/components/LoadingSpinner";
import { buildAction, getIcon } from "@/utils/chat";
import {
  formatSubAgentDuration,
  isAgentDispatchTask,
  resolveSubAgentDisplay,
} from "@/utils/chat/subagent";
import {
  buildDeepSearchPreviewModel,
  resolveDeepSearchStage,
  shouldRenderDeepSearchPreview,
} from "@/utils/deepSearch";
import { getTaskFiles } from "@/utils/taskArtifacts";
import {
  Reasoning,
  ReasoningTrigger,
  ReasoningContent,
} from "@/components/ai-elements/reasoning";
import {
  BotIcon,
  CheckIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  LoaderCircleIcon,
  FileTextIcon,
  SearchIcon,
  UserIcon,
} from "lucide-react";
import { resolveTaskSummaryText } from "./contentHelpers";
import {
  isTimelineTaskContainerCompleted,
  shouldShowTimelineGroupCompletedIcon,
} from "./timelineStatus";
import {
  canOpenTaskWorkspacePanel,
  isTimelineToolActive,
} from "@/components/ChatView/streamState";
import PlanApprovalCard from "./PlanApprovalCard";
import SessionTaskList from "./SessionTaskList";
import UserBriefCard from "./UserBriefCard";

type TimelineProps = {
  chat: CHAT.ChatItem;
  isPlanSolveMessage: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
};

type ToolItemProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  changePlan?: () => void;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
};

const taskRowClass =
  "mt-2 flex w-full max-w-full cursor-pointer items-center gap-3 rounded-xl border border-[var(--chat-border)]/40 bg-white px-2.5 py-2 transition-colors duration-200 hover:border-[var(--chat-border)] hover:bg-[#fafafa]";

const taskIconClass =
  "flex size-7 shrink-0 items-center justify-center text-[var(--chat-text-muted)] [&_svg]:drop-shadow-none [&_svg]:[filter:none]";

const taskTitleClass =
  "shrink-0 text-[14px] font-medium text-[var(--chat-text)]";

const taskMetaClass =
  "truncate text-[13px] text-[var(--chat-text-soft)]";

export const ToolItem: FC<ToolItemProps> = memo(({
  tool,
  chat,
  changePlan,
  changeActiveChat,
  changeFile,
}) => {
  const actionInfo = useMemo(() => buildAction(tool), [tool]);

  switch (tool.messageType) {
    case "plan": {
      const completedIndex = tool.plan?.stepStatus.lastIndexOf("completed") || 0;
      return (
        <div
          className={taskRowClass}
          onClick={() => changePlan?.()}
        >
          <div className={taskIconClass}>
            <i className={`font_family ${getIcon(tool.messageType)} text-[17px] leading-none [text-shadow:none]`}></i>
          </div>
          <div className="flex min-w-0 items-center gap-2 overflow-hidden">
            <span className={taskTitleClass}>已完成</span>
            <span className={taskMetaClass}>
              {tool.plan?.steps[completedIndex]}
            </span>
          </div>
        </div>
      );
    }
    case "llm_reasoning": {
      const streamingThought = !tool.resultMap?.isFinal;
      return (
        <div className="mt-[8px] rounded-2xl border border-[var(--chat-border)]/18 bg-[var(--chat-surface-soft)]/38 px-3 py-2.5">
          <Reasoning isStreaming={streamingThought} defaultOpen={false}>
            <ReasoningTrigger />
            <ReasoningContent>{tool.toolThought || ""}</ReasoningContent>
          </Reasoning>
        </div>
      );
    }
    case "tool_thought": {
      // 助手过程回复：常显，不当深度思考
      const text = (tool.toolThought || "").trim();
      if (!text) {
        return null;
      }
      return (
        <div className="mt-2 px-1 text-[14px] leading-7 text-[var(--chat-text)]">
          {text}
        </div>
      );
    }
    case "ask_user_question": {
      return (
        <div className="mt-2 rounded-2xl border border-[var(--chat-border)]/40 bg-[var(--chat-surface-soft)]/50 px-4 py-3 text-[13px] text-[var(--chat-text-soft)]">
          需要你的选择 · 请在右侧工作区作答
        </div>
      );
    }
    case "plan_approval": {
      return <PlanApprovalCard tool={tool} />;
    }
    case "session_tasks": {
      return <SessionTaskList tool={tool} chat={chat} />;
    }
    case "user_brief": {
      return <UserBriefCard tool={tool} />;
    }
    case "ui_tree": {
      // 完整树已在 Dialogue 主回复区 featured 展示；时间线只保留轻量步骤摘要。
      const nested: any = tool.resultMap?.resultMap || tool.resultMap || {};
      const patchCount = Array.isArray(nested.appliedPatches)
        ? nested.appliedPatches.length
        : Number(nested.patchCount) || 0;
      return (
        <div className="mt-2 rounded-xl border border-[var(--chat-border)]/50 bg-[var(--chat-surface-soft)]/40 px-3 py-2 text-[13px] text-[var(--chat-text-soft)]">
          已生成 GenUI 界面
          {patchCount > 0 ? `（已合并 ${patchCount} 条补丁）` : ""}
          <span className="ml-1 text-[12px] text-[var(--chat-text-muted)]">
            · 见下方可视化回复
          </span>
        </div>
      );
    }
    case "ui_patch": {
      const nested: any = tool.resultMap?.resultMap || tool.resultMap || {};
      const count = Array.isArray(nested.patches)
        ? nested.patches.length
        : Number(nested.patchCount) || 0;
      return (
        <div className="mt-2 rounded-xl border border-[var(--chat-border)]/50 bg-[var(--chat-surface-soft)]/40 px-3 py-2 text-[13px] text-[var(--chat-text-soft)]">
          GenUI 补丁（{count || "?"} 条）
          <span className="ml-1 text-[12px] text-[var(--chat-text-muted)]">
            · 已更新下方可视化回复
          </span>
        </div>
      );
    }
    case "browser": {
      return (
        <div className="mt-[8px]">
          {(tool.resultMap?.steps || [])
            .filter((step) => step.status !== "completed")
            .map((step, index) => (
              <div key={`${step.goal}-${index}`}>
                <i className={`font_family ${getIcon(tool.messageType)}`}></i>
                <div>
                  <div>{actionInfo.action}</div>
                  <div>{step.goal}</div>
                </div>
              </div>
            ))}
        </div>
      );
    }
    case "task_summary": {
      const attachmentFiles = getTaskFiles(tool);
      return (
        <div className="mt-[8px]">
          <div className="mb-[8px]">{resolveTaskSummaryText(tool) || "任务已完成"}</div>
          <AttachmentList
            files={attachmentFiles}
            preview={true}
            review={(file) => changeFile?.(file, chat)}
          />
        </div>
      );
    }
    default: {
      const loadingType = ["html", "markdown", "data_analysis"];
      const deepSearchStage =
        tool.messageType === "deep_search"
          ? resolveDeepSearchStage(tool.resultMap?.messageType)
          : undefined;
      const loading =
        !tool.resultMap?.isFinal &&
        ((tool.messageType === "deep_search" &&
          (deepSearchStage === "extend" || deepSearchStage === "report")) ||
          loadingType.includes(tool.messageType));
      const isSearching =
        tool.messageType === "deep_search" &&
        deepSearchStage !== "report";
      const isSummarizing =
        tool.messageType === "deep_search" && deepSearchStage === "report";
      const isDeepSearchInline = isSearching || isSummarizing;
      const isSubAgent = isAgentDispatchTask(tool);
      const subAgent = isSubAgent ? resolveSubAgentDisplay(tool) : null;

      if (isSubAgent && subAgent) {
        return (
          <SubAgentTimelineCard
            tool={tool}
            chat={chat}
            subAgent={subAgent}
            actionInfo={{
              action: actionInfo.action,
              tool: actionInfo.tool,
              name: actionInfo.name || "",
            }}
            changeActiveChat={changeActiveChat}
          />
        );
      }

      const canOpenPanel = canOpenTaskWorkspacePanel(tool);
      return (
        <div
          className={
            canOpenPanel
              ? taskRowClass
              : "mt-2 flex w-full max-w-full cursor-default items-center gap-3 rounded-xl border border-[var(--chat-border)]/40 bg-white px-2.5 py-2"
          }
          onClick={
            canOpenPanel ? () => changeActiveChat(tool, chat) : undefined
          }
        >
          {isDeepSearchInline ? (
            <div className={taskIconClass}>
              {loading ? (
                <LoaderCircleIcon className="size-4 animate-spin" />
              ) : isSearching ? (
                <SearchIcon className="size-4" />
              ) : (
                <FileTextIcon className="size-4" />
              )}
            </div>
          ) : loading ? (
            <div className={taskIconClass}>
              <LoaderCircleIcon className="size-4 animate-spin" />
            </div>
          ) : (
              <div className={taskIconClass}>
                <i
                  className={`font_family ${getIcon(
                    tool.messageType === "deep_search" &&
                      tool.resultMap.messageType === "report"
                      ? "file"
                      : tool.messageType
                  )} text-[17px] leading-none text-[var(--chat-text-muted)] [text-shadow:none]`}
                ></i>
              </div>
          )}
          <div className="flex min-w-0 items-center gap-2 overflow-hidden">
            <span className={taskTitleClass}>
              {actionInfo.action}
            </span>
            <span className={taskMetaClass}>
              {actionInfo.name}
            </span>
          </div>
        </div>
      );
    }
  }
}, (prevProps, nextProps) =>
  prevProps.tool === nextProps.tool &&
  prevProps.chat === nextProps.chat &&
  prevProps.changePlan === nextProps.changePlan &&
  prevProps.changeActiveChat === nextProps.changeActiveChat &&
  prevProps.changeFile === nextProps.changeFile
);

ToolItem.displayName = "ToolItem";

const SubAgentTimelineCard: FC<{
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  subAgent: ReturnType<typeof resolveSubAgentDisplay>;
  actionInfo: { action: string; tool: string; name: string };
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
}> = memo(({ tool, chat, subAgent, actionInfo, changeActiveChat }) => {
  const nested = tool.children || [];
  const nestedCount = nested.length;
  // 有嵌套工具时默认展开，避免历史回放/完成后折叠导致「看不了子工具」
  const [expanded, setExpanded] = useState(
    () => subAgent.status === "running" || nestedCount > 0
  );
  const duration = formatSubAgentDuration(subAgent.totalDurationMs);
  const subAgentRunning = subAgent.status === "running";
  const subAgentFailed = subAgent.status === "failed";
  const canOpenPanel = canOpenTaskWorkspacePanel(tool);

  useEffect(() => {
    // 运行中的子 Agent 必须自动展开以展示实时工具。
    if (subAgentRunning) {
      setExpanded(true);
    }
  }, [subAgentRunning]);

  return (
    <div className="mt-2">
      <div
        className={[
          canOpenPanel
            ? taskRowClass
            : "flex w-full max-w-full cursor-default items-center gap-3 rounded-xl border bg-white px-2.5 py-2",
          "border-[var(--chat-border)]/50 bg-white",
          subAgentRunning ? "border-[var(--chat-border)]" : "",
          subAgentFailed ? "border-[#d1d1d6]" : "",
        ].join(" ")}
        onClick={
          canOpenPanel ? () => changeActiveChat(tool, chat) : undefined
        }
      >
        <button
          type="button"
          className="flex size-6 shrink-0 items-center justify-center rounded-md text-[var(--chat-text-soft)] hover:bg-[var(--chat-interactive-hover)]"
          onClick={(event) => {
            event.stopPropagation();
            setExpanded((value) => !value);
          }}
          aria-label={expanded ? "折叠子工具" : "展开子工具"}
        >
          {expanded ? (
            <ChevronDownIcon className="size-3.5" />
          ) : (
            <ChevronRightIcon className="size-3.5" />
          )}
        </button>
        <div
          className={[
            "relative flex size-7 shrink-0 items-center justify-center rounded-lg border",
            subAgentFailed
              ? "border-red-400/50 text-red-500"
              : subAgentRunning
                ? "border-[var(--chat-accent)]/45 text-[var(--chat-accent)]"
                : "border-[var(--chat-border)] text-[var(--chat-accent)]",
          ].join(" ")}
        >
          {subAgentRunning ? (
            <span
              className="absolute -inset-0.5 rounded-[10px] border border-transparent border-t-[var(--chat-accent)] opacity-80 motion-safe:animate-spin"
              aria-hidden
            />
          ) : null}
          {subAgentRunning ? (
            <LoaderCircleIcon className="relative z-[1] size-4 animate-spin" />
          ) : (
            <BotIcon className="relative z-[1] size-4" />
          )}
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-0.5 overflow-hidden">
          <div className="flex min-w-0 items-center gap-2 overflow-hidden">
            <span className={taskTitleClass}>{actionInfo.action}</span>
            <span className="shrink-0 rounded-md bg-[var(--chat-accent)]/12 px-1.5 py-0.5 text-[11px] font-medium text-[var(--chat-accent)]">
              {subAgent.subagentType}
            </span>
            {subAgent.description ? (
              <span className={taskMetaClass}>{subAgent.description}</span>
            ) : null}
          </div>
          <div className="flex min-w-0 items-center gap-2 overflow-hidden text-[12px] text-[var(--chat-text-soft)]">
            {subAgentRunning ? (
              <span className="truncate">
                同步执行中{nestedCount > 0 ? ` · ${nestedCount} tools` : "…"}
              </span>
            ) : (
              <>
                {nestedCount > 0 ? (
                  <span className="shrink-0">{nestedCount} tools</span>
                ) : subAgent.totalToolUseCount != null ? (
                  <span className="shrink-0">{subAgent.totalToolUseCount} tools</span>
                ) : null}
                {duration ? <span className="shrink-0">{duration}</span> : null}
                {subAgent.agentId ? (
                  <span className="truncate font-mono text-[11px] opacity-70">
                    {subAgent.agentId.slice(0, 8)}
                  </span>
                ) : null}
              </>
            )}
          </div>
        </div>
      </div>
      {expanded && nestedCount > 0 ? (
        <div className="ml-6 border-l border-[var(--chat-border)]/40 pl-3">
          {nested.map((child, index) => {
            const childAction = buildAction(child);
            const childLoading =
              child.messageType === "tool_call" && !child.resultMap?.isFinal;
            const canOpenChildPanel = canOpenTaskWorkspacePanel(child);
            return (
              <div
                key={child.id || child.messageId || child.taskId || index}
                className={
                  canOpenChildPanel
                    ? "mt-1.5 flex min-w-0 cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-[13px] text-[var(--chat-text-soft)] hover:bg-[var(--chat-interactive-hover)]"
                    : "mt-1.5 flex min-w-0 cursor-default items-center gap-2 rounded-lg px-2 py-1.5 text-[13px] text-[var(--chat-text-soft)]"
                }
                onClick={(event) => {
                  event.stopPropagation();
                  if (!canOpenChildPanel) {
                    return;
                  }
                  changeActiveChat(child, chat);
                }}
              >
                {childLoading ? (
                  <LoaderCircleIcon className="size-3.5 shrink-0 animate-spin text-[var(--chat-text-muted)]" />
                ) : (
                  <i
                    className={`font_family ${getIcon(child.messageType)} shrink-0 text-[14px] text-[var(--chat-text-muted)]`}
                  />
                )}
                <span className="shrink-0 font-medium text-[var(--chat-text)]">
                  {childAction.action}
                </span>
                <span className="truncate">
                  {childAction.name || childAction.tool || child.toolResult?.toolName || child.resultMap?.toolName}
                </span>
              </div>
            );
          })}
        </div>
      ) : null}
    </div>
  );
});

SubAgentTimelineCard.displayName = "SubAgentTimelineCard";

export const DeepSearchPreviewItem: FC<{
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
}> = memo(({ tool, chat, changeActiveChat }) => {
  const model = useMemo(() => buildDeepSearchPreviewModel(tool), [tool]);

  if (!model) {
    return null;
  }

  const clickable = model.interactive;
  const handleClick = () => {
    if (clickable) {
      changeActiveChat(tool, chat);
    }
  };

  return (
    <motion.div
      initial={{
        opacity: 0,
        y: 8,
      }}
      animate={{
        opacity: 1,
        y: 0,
      }}
      transition={{
        duration: 0.2,
        ease: [0.25, 0.46, 0.45, 0.94],
      }}
      className={[
        "mt-2 overflow-hidden rounded-2xl border border-[var(--chat-border)]/50",
        "bg-white px-4 py-3 ring-0",
        clickable
          ? "cursor-pointer transition-colors duration-200 hover:bg-[#fafafa]"
          : "",
      ].join(" ")}
      onClick={handleClick}
      onKeyDown={(event) => {
        if (!clickable) {
          return;
        }
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          handleClick();
        }
      }}
      role={clickable ? "button" : undefined}
      tabIndex={clickable ? 0 : undefined}
    >
      <div className="flex items-start gap-3">
        <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-[var(--chat-border)]/40 bg-[#f5f5f7] text-[var(--chat-text-muted)]">
          {model.loading ? (
            <LoaderCircleIcon className="size-4 animate-spin" />
          ) : (
            <SearchIcon className="size-4" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="truncate text-[14px] font-medium leading-snug tracking-[-0.01em] text-[var(--chat-text)]">
              {model.query}
            </span>
            <span className="inline-flex shrink-0 items-center rounded-full border border-[var(--chat-border)]/40 bg-[#f5f5f7] px-2 py-0.5 text-[11px] font-medium text-[var(--chat-text-muted)]">
              {model.statusLabel}
            </span>
          </div>
          <p className="mt-1 text-[12px] leading-relaxed text-[var(--chat-text-soft)]">
            {model.description}
          </p>
        </div>
      </div>
    </motion.div>
  );
});

DeepSearchPreviewItem.displayName = "DeepSearchPreviewItem";

const resolveDigitalEmployee = (task: CHAT.Task): string | undefined => {
  return task.children?.find((child) => child.digitalEmployee)?.digitalEmployee;
};

const TimelineTaskBlock: FC<{
  chat: CHAT.ChatItem;
  task: CHAT.Task;
  isPlanSolveMessage: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
}> = ({ chat, task, isPlanSolveMessage, changeActiveChat, changePlan, changeFile }) => {
  const children = task.children || [];
  const digitalEmployee = resolveDigitalEmployee(task);
  const taskCompleted = isTimelineTaskContainerCompleted(task);
  const hasActiveChild = children.some((tool) => isTimelineToolActive(tool));
  const canCollapse = children.length > 1 && taskCompleted && !hasActiveChild;
  const [expanded, setExpanded] = useState(() => !canCollapse);

  useEffect(() => {
    // active 子工具优先级高于完成后的默认折叠：实时事件到达时展开，整组完成且
    // 没有活动子项时收起。这里同步的是展示状态，不会修改后端任务数据。
    if (hasActiveChild) {
      setExpanded(true);
    } else if (canCollapse) {
      setExpanded(false);
    }
  }, [canCollapse, hasActiveChild]);

  return (
    <div
      className="overflow-hidden"
    >
      {isPlanSolveMessage && task.task ? (
        <div className="mb-1">
          <button
            type="button"
            className="flex w-full items-center gap-2 text-left"
            onClick={() => {
              if (canCollapse) {
                setExpanded((value) => !value);
              }
            }}
            disabled={!canCollapse}
          >
            {canCollapse ? (
              expanded ? (
                <ChevronDownIcon className="size-3.5 shrink-0 text-[var(--chat-text-soft)]" />
              ) : (
                <ChevronRightIcon className="size-3.5 shrink-0 text-[var(--chat-text-soft)]" />
              )
            ) : null}
            <div className="min-w-0 flex-1 font-[500]">{task.task}</div>
            {canCollapse && !expanded ? (
              <span className="shrink-0 text-[12px] text-[var(--chat-text-soft)]">
                {children.length} 步已完成
              </span>
            ) : null}
          </button>
          {digitalEmployee && (
            <div className="mt-1.5 inline-flex items-center gap-2 rounded-lg border border-[var(--chat-border)]/18 bg-[var(--chat-surface)]/80 px-3 py-1.5 text-[13px]">
              <UserIcon className="h-3.5 w-3.5 text-[var(--chat-text-muted)]" />
              <span className="text-[var(--chat-text-soft)]">{digitalEmployee}</span>
              {taskCompleted && (
                <>
                  <span className="text-[var(--chat-border)]">|</span>
                  <CheckIcon className="h-3.5 w-3.5 text-[var(--status-success-text)]" />
                </>
              )}
            </div>
          )}
        </div>
      ) : null}
      {!isPlanSolveMessage && canCollapse ? (
        <button
          type="button"
          className="mt-1 flex w-full items-center gap-2 rounded-xl px-2 py-1.5 text-left text-[13px] text-[var(--chat-text-soft)] hover:bg-[var(--chat-interactive-hover)]"
          onClick={() => setExpanded((value) => !value)}
        >
          {expanded ? (
            <ChevronDownIcon className="size-3.5 shrink-0" />
          ) : (
            <ChevronRightIcon className="size-3.5 shrink-0" />
          )}
          <span className="font-medium text-[var(--chat-text)]">
            {expanded ? "收起已完成步骤" : `已完成 ${children.length} 步`}
          </span>
        </button>
      ) : null}
      {expanded || !canCollapse
        ? children.map((tool, index) => {
            const stage =
              tool.messageType === "deep_search"
                ? resolveDeepSearchStage(tool.resultMap?.messageType)
                : undefined;
            const shouldRenderPreview =
              tool.messageType === "deep_search" &&
              shouldRenderDeepSearchPreview(stage);

            return (
              <div
                key={tool.id || tool.messageId || tool.taskId || index}
                className="overflow-hidden"
              >
                {shouldRenderPreview ? (
                  <DeepSearchPreviewItem
                    tool={tool}
                    chat={chat}
                    changeActiveChat={changeActiveChat}
                  />
                ) : (
                  <ToolItem
                    tool={tool}
                    chat={chat}
                    changePlan={changePlan}
                    changeActiveChat={changeActiveChat}
                    changeFile={changeFile}
                  />
                )}
              </div>
            );
          })
        : null}
    </div>
  );
};

const TimelineContent: FC<{
  chat: CHAT.ChatItem;
  tasks: CHAT.Task[];
  isPlanSolveMessage: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
}> = ({ chat, tasks, isPlanSolveMessage, changeActiveChat, changePlan, changeFile }) => {
  return (
    <>
      {tasks.map((task, taskIndex) => (
        <TimelineTaskBlock
          key={task.id || task.messageId || task.taskId || taskIndex}
          chat={chat}
          task={task}
          isPlanSolveMessage={isPlanSolveMessage}
          changeActiveChat={changeActiveChat}
          changePlan={changePlan}
          changeFile={changeFile}
        />
      ))}
    </>
  );
};

export const Timeline: FC<TimelineProps> = ({
  chat,
  isPlanSolveMessage,
  changeActiveChat,
  changePlan,
  changeFile,
}) => (
  <>
    {/* 外层 tasks 是后端事件分组；PlanSolve 还要保留左侧计划进度轨道，普通
        ReAct 则只渲染内容列，避免用不存在的计划序号制造视觉状态。 */}
    {chat.tasks.map((tasks, index) => {
      const lastTask = index === chat.tasks.length - 1;
      const groupKey = tasks[0]?.id || tasks[0]?.messageId || tasks[0]?.taskId || index;
      const showCompletedIcon = shouldShowTimelineGroupCompletedIcon({
        isPlanSolve: isPlanSolveMessage,
        isLastGroup: lastTask,
        loading: chat.loading,
        tasks,
      });

      return (
        <div className="flex w-full" key={groupKey}>
          {isPlanSolveMessage ? (
            <div className="relative mb-2 mt-1 w-8 shrink-0 overflow-hidden">
              {lastTask && chat.loading ? (
                <div aria-label="timeline-loading">
                  <LoadingSpinner />
                </div>
              ) : showCompletedIcon ? (
                <i
                  aria-label="timeline-completed"
                  className="font_family icon-yiwanchengtianchong absolute left-0 top-0 text-[16px] text-[var(--chat-accent)]"
                ></i>
              ) : null}
            </div>
          ) : null}
          <div className="mb-2 flex-1 overflow-hidden">
            <TimelineContent
              chat={chat}
              tasks={tasks}
              isPlanSolveMessage={isPlanSolveMessage}
              changeActiveChat={changeActiveChat}
              changePlan={changePlan}
              changeFile={changeFile}
            />
          </div>
        </div>
      );
    })}
  </>
);

Timeline.displayName = "Timeline";
