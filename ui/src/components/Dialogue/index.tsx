import { FC, useState, useCallback, useMemo, memo, useEffect, useRef } from "react";
import { motion } from "motion/react";
import AttachmentList from "@/components/AttachmentList";
import LoadingSpinner from "@/components/LoadingSpinner";
import { buildAction, getIcon } from "@/utils/chat";
import {
  buildDeepSearchPreviewModel,
  resolveDeepSearchStage,
  shouldRenderDeepSearchPreview,
} from "@/utils/deepSearch";
import { getTaskFiles } from "@/utils/taskArtifacts";
import {
  Message,
  MessageContent,
  MessageActions,
  MessageAction,
} from "@/components/ai-elements/message";
import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import {
  Reasoning,
  ReasoningTrigger,
  ReasoningContent,
} from "@/components/ai-elements/reasoning";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  CopyIcon,
  CheckIcon,
  RefreshCwIcon,
  MoreHorizontalIcon,
  LoaderCircleIcon,
  FileTextIcon,
  Layers,
  SearchIcon,
  UserIcon,
} from "lucide-react";
import {
  normalizeMarkdownForDisplay,
  type MarkdownNormalizationScope,
} from "@/utils/markdown";
import RunStatus from "@/components/ActionView/RunStatus";
import {
  isPlanSolveConversation,
  isStructuredConversation,
} from "@/utils/agentMode";
import {
  buildPlannerRoundsForDisplay,
  syncPlannerVersionCursor,
} from "./plannerHistory";
import {
  isTimelineTaskContainerCompleted,
  shouldShowTimelineGroupCompletedIcon,
} from "./timelineStatus";

type Props = {
  chat: CHAT.ChatItem;
  streamingThought?: string;
  deepThink: boolean;
  changeTask?: (task: CHAT.Task, chat?: CHAT.ChatItem) => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
  changePlan?: () => void;
  onRegenerate?: () => void;
};

const normalizePlanForDisplay = (plan?: CHAT.Plan) => {
  if (!plan) {
    return null;
  }

  const steps = Array.isArray(plan.steps) ? plan.steps : [];
  const stages =
    Array.isArray(plan.stages) && plan.stages.length ? plan.stages : steps;
  const stepStatus =
    Array.isArray(plan.stepStatus) && plan.stepStatus.length
      ? plan.stepStatus
      : Array.from({ length: stages.length }, () => "completed");

  return {
    title: plan.title || "执行计划",
    stages,
    steps,
    stepStatus,
  };
};

const resolvePlanStepDetail = (plan: ReturnType<typeof normalizePlanForDisplay>, index: number) => {
  if (!plan) {
    return "";
  }

  if (
    Array.isArray(plan.stages) &&
    Array.isArray(plan.steps) &&
    plan.stages[index] === plan.steps[index]
  ) {
    return "";
  }

  return plan.steps[index] || "";
};

const resolvePlanStepTone = (status?: string) => {
  switch (status) {
    case "completed":
      return {
        badgeClass: "bg-[#0071e3]/10 text-[#0071e3]",
        dotClass: "bg-[#0071e3]",
        label: "已完成",
      };
    case "in_progress":
      return {
        badgeClass: "bg-amber-500/10 text-amber-600",
        dotClass: "bg-amber-500",
        label: "进行中",
      };
    default:
      return {
        badgeClass: "bg-[var(--chat-surface-muted)] text-[var(--chat-text-muted)]",
        dotClass: "bg-[var(--chat-text-muted)]",
        label: "未开始",
      };
  }
};

const PlanSection: FC<{
  plan?: CHAT.Plan;
  versionLabel?: string;
  onPrev?: () => void;
  onNext?: () => void;
  canPrev?: boolean;
  canNext?: boolean;
  staticSnapshot?: boolean;
}> = memo(({
  plan,
  versionLabel,
  onPrev,
  onNext,
  canPrev,
  canNext,
  staticSnapshot = false,
}) => {
  const normalizedPlan = useMemo(() => normalizePlanForDisplay(plan), [plan]);

  if (!normalizedPlan || !normalizedPlan.stages.length) {
    return null;
  }

  const completedCount = normalizedPlan.stepStatus.filter(
    (status) => status === "completed"
  ).length;

  return (
    <motion.div
      initial={{
        opacity: 0,
        y: 10
      }}
      animate={{
        opacity: 1,
        y: 0
      }}
      transition={{
        duration: 0.24,
        ease: [0.25, 0.46, 0.45, 0.94]
      }}
      className="overflow-hidden rounded-2xl bg-[var(--chat-surface-soft)]/90 px-4 py-4 shadow-[var(--shadow-sm)] ring-0"
    >
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[var(--chat-surface)]/95 text-[var(--chat-text-soft)] shadow-[var(--shadow-xs)]">
            <Layers className="h-5 w-5" strokeWidth={1.75} />
          </div>
          <div className="min-w-0">
            <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--chat-text-muted)]">
              研究路线
            </p>
            <p
              className="text-[15px] font-semibold leading-snug tracking-[-0.02em] text-[var(--chat-text)]"
              style={{ fontFamily: "var(--font-sans)" }}
            >
              {normalizedPlan.title}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {versionLabel ? (
            <div className="inline-flex items-center gap-1 rounded-full bg-[var(--chat-surface)] px-2 py-1 text-[11px] font-medium text-[var(--chat-text-soft)]">
              <button
                type="button"
                className="rounded px-1 disabled:opacity-40"
                onClick={onPrev}
                disabled={!canPrev}
              >
                {"<"}
              </button>
              <span>{versionLabel}</span>
              <button
                type="button"
                className="rounded px-1 disabled:opacity-40"
                onClick={onNext}
                disabled={!canNext}
              >
                {">"}
              </button>
            </div>
          ) : null}
          {!staticSnapshot ? (
            <div className="shrink-0 rounded-full bg-[var(--chat-surface)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
              {completedCount}/{normalizedPlan.stages.length}
            </div>
          ) : (
            <div className="shrink-0 rounded-full bg-[var(--chat-surface)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
              历史快照
            </div>
          )}
        </div>
      </div>
      <div className="space-y-2.5">
        {normalizedPlan.stages.map((stage, index) => {
          const status = normalizedPlan.stepStatus[index];
          const tone = resolvePlanStepTone(status);
          const stepDetail = resolvePlanStepDetail(normalizedPlan, index);

          return (
            <motion.div
              key={`${stage}-${index}`}
              initial={{
                opacity: 0,
                x: -6
              }}
              animate={{
                opacity: 1,
                x: 0
              }}
              transition={{
                delay: Math.min(index * 0.06, 0.36),
                duration: 0.22,
                ease: [0.25, 0.46, 0.45, 0.94],
              }}
              className="rounded-xl bg-[var(--chat-surface)]/75 px-3 py-3 shadow-[var(--shadow-xs)]"
            >
              <div className="flex items-start gap-3">
                <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[var(--chat-surface-muted)] text-[12px] font-semibold tabular-nums text-[var(--chat-text-soft)]">
                  {index + 1}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-[14px] font-medium leading-snug tracking-[-0.01em] text-[var(--chat-text)]">
                      {stage}
                    </span>
                    {!staticSnapshot ? (
                      <span
                        className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${tone.badgeClass}`}
                      >
                        <span className={`h-1.5 w-1.5 rounded-full ${tone.dotClass}`}></span>
                        {tone.label}
                      </span>
                    ) : null}
                  </div>
                  {stepDetail ? (
                    <p className="mt-2 text-[13px] leading-relaxed text-[var(--chat-text-soft)]">
                      {stepDetail}
                    </p>
                  ) : null}
                </div>
              </div>
            </motion.div>
          );
        })}
      </div>
    </motion.div>
  );
});

PlanSection.displayName = "PlanSection";

const ToolItem: FC<{
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  changePlan?: () => void;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
}> = memo(({ tool, chat, changePlan, changeActiveChat, changeFile }) => {
  const actionInfo = useMemo(() => buildAction(tool), [tool]);
  switch (tool.messageType) {
    case "plan": {
      const completedIndex = tool.plan?.stepStatus.lastIndexOf("completed") || 0;
      return (
        <div
          className="mt-2 flex w-full max-w-full cursor-pointer items-center gap-3 rounded-xl px-1 py-2 transition-all duration-200 hover:bg-muted/35"
          onClick={() => changePlan?.()}
        >
          <div className="flex size-7 shrink-0 items-center justify-center text-[#0071e3] [&_svg]:drop-shadow-none [&_svg]:[filter:none]">
            <i className={`font_family ${getIcon(tool.messageType)} text-[17px] leading-none [text-shadow:none]`}></i>
          </div>
          <div className="flex min-w-0 items-center gap-2 overflow-hidden">
            <span className="shrink-0 text-[14px] font-medium text-foreground">已完成</span>
            <span className="truncate text-[13px] text-muted-foreground">
              {tool.plan?.steps[completedIndex]}
            </span>
          </div>
        </div>
      );
    }
    case "tool_thought": {
      const streamingThought = !tool.resultMap?.isFinal;
      return (
        <div className="mt-[8px] rounded-2xl border border-[var(--chat-border)]/18 bg-[var(--chat-surface-soft)]/38 px-3 py-2.5">
          <Reasoning isStreaming={streamingThought} defaultOpen>
            <ReasoningTrigger />
            <ReasoningContent>{tool.toolThought || ""}</ReasoningContent>
          </Reasoning>
        </div>
      );
    }
    case "browser": {
      return (
        <div className="mt-[8px]">
          {(tool.resultMap?.steps || [])
            .filter((s) => s.status !== "completed")
            .map((s, idx) => (
              <div key={`${s.goal}-${idx}`}>
                <i className={`font_family ${getIcon(tool.messageType)}`}></i>
                <div>
                  <div>{actionInfo.action}</div>
                  <div>{s.goal}</div>
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

      return (
        <div
          className={
            "mt-2 flex w-full max-w-full cursor-pointer items-center gap-3 rounded-xl px-1 py-2 transition-all duration-200 hover:bg-muted/35"
          }
          onClick={() => changeActiveChat(tool, chat)}
        >
          {isDeepSearchInline ? (
            <div className="flex size-7 shrink-0 items-center justify-center text-primary [&_svg]:drop-shadow-none [&_svg]:[filter:none]">
              {loading ? (
                <LoaderCircleIcon className="size-4 animate-spin" />
              ) : isSearching ? (
                <SearchIcon className="size-4" />
              ) : (
                <FileTextIcon className="size-4" />
              )}
            </div>
          ) : loading ? (
            <div className="flex size-7 shrink-0 items-center justify-center text-primary [&_svg]:drop-shadow-none [&_svg]:[filter:none]">
              <LoaderCircleIcon className="size-4 animate-spin" />
            </div>
          ) : (
            <div
              className="flex size-7 shrink-0 items-center justify-center [&_svg]:drop-shadow-none [&_svg]:[filter:none]"
              style={{ color: tool.messageType === "code" ? "#111827" : "#0071e3" }}
            >
              <i
                className={`font_family ${getIcon(
                  tool.messageType === "deep_search" &&
                    tool.resultMap.messageType === "report"
                    ? "file"
                    : tool.messageType
                )} text-[17px] leading-none [text-shadow:none]`}
              ></i>
            </div>
          )}
          <div className="flex min-w-0 items-center gap-2 overflow-hidden">
            <span className="shrink-0 text-[14px] font-medium text-foreground">
              {actionInfo.action}
            </span>
            <span className="truncate text-[13px] text-muted-foreground">
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

const DeepSearchPreviewItem: FC<{
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
        "mt-2 overflow-hidden rounded-2xl border border-[var(--chat-border)]/18",
        "bg-[var(--chat-surface-soft)]/72 px-4 py-3 shadow-[var(--shadow-xs)] ring-0",
        clickable
          ? "cursor-pointer transition-all duration-200 hover:bg-[var(--chat-surface-muted)]/78 hover:shadow-[var(--shadow-sm)]"
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
        <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[var(--chat-surface)]/90 text-[var(--chat-text-muted)]">
          {model.loading ? (
            <LoaderCircleIcon className="size-4 animate-spin" />
          ) : (
            <SearchIcon className="size-4" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span
              className="truncate text-[14px] font-medium leading-snug tracking-[-0.01em] text-[var(--chat-text)]"
            >
              {model.query}
            </span>
            <span className="inline-flex shrink-0 items-center rounded-full bg-[var(--chat-surface)] px-2 py-0.5 text-[11px] font-medium text-[var(--chat-text-muted)]">
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

const TimeLineContent: FC<{
  chat: CHAT.ChatItem;
  tasks: CHAT.Task[];
  isPlanSolveMessage: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
}> = ({ chat, tasks, isPlanSolveMessage, changeActiveChat, changePlan, changeFile }) => {
  return (
    <>
      {tasks.map((task, taskIndex) => {
        const digitalEmployee = resolveDigitalEmployee(task);
        const taskCompleted = isTimelineTaskContainerCompleted(task);
        return (
          <div
            key={task.id || task.messageId || task.taskId || taskIndex}
            className="overflow-hidden"
          >
            {isPlanSolveMessage && task.task ? (
              <div className="mb-1">
                <div className="font-[500]">{task.task}</div>
                {digitalEmployee && (
                  <div className="mt-1.5 inline-flex items-center gap-2 rounded-lg border border-[var(--chat-border)]/18 bg-[var(--chat-surface)]/80 px-3 py-1.5 text-[13px]">
                    <UserIcon className="h-3.5 w-3.5 text-[var(--chat-text-muted)]" />
                    <span className="text-[var(--chat-text-soft)]">{digitalEmployee}</span>
                    {taskCompleted && (
                      <>
                        <span className="text-[var(--chat-border)]">|</span>
                        <CheckIcon className="h-3.5 w-3.5 text-green-500" />
                      </>
                    )}
                  </div>
                )}
              </div>
            ) : null}
            {(task.children || []).map((tool, index) => {
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
            })}
          </div>
        );
      })}
    </>
  );
};

const TimeLine: FC<{
  chat: CHAT.ChatItem;
  isPlanSolveMessage: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
}> = ({ chat, isPlanSolveMessage, changeActiveChat, changePlan, changeFile }) => (
  <>
    {chat.tasks.map((t, i) => {
      const lastTask = i === chat.tasks.length - 1;
      const groupKey = t[0]?.id || t[0]?.messageId || t[0]?.taskId || i;
      const showCompletedIcon = shouldShowTimelineGroupCompletedIcon({
        isPlanSolve: isPlanSolveMessage,
        isLastGroup: lastTask,
        loading: chat.loading,
        tasks: t,
      });
      return (
        <div className="flex w-full" key={groupKey}>
          {isPlanSolveMessage ? (
            <div className="relative mb-2 mt-1 w-8 shrink-0 overflow-hidden">
              {lastTask && chat.loading ? (
                <LoadingSpinner/>
              ) : showCompletedIcon ? (
                <i className="font_family icon-yiwanchengtianchong absolute left-0 top-0 text-[16px] text-[#0071e3]"></i>
              ) : null}
            </div>
          ) : null}
          <div className="mb-2 flex-1 overflow-hidden">
            <TimeLineContent
              chat={chat}
              tasks={t}
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

function resolveTaskSummaryText(task?: CHAT.Task) {
  if (!task) {
    return "";
  }

  const taskRecord = task as Record<string, any>;
  const resultMapRecord = (task.resultMap || {}) as Record<string, any>;
  return (
    resultMapRecord.taskSummary ||
    taskRecord.taskSummary ||
    task.result ||
    resultMapRecord.result ||
    ""
  );
}

/**
 * 结构化总结单独启用增强规范化，避免误伤普通聊天和其他 Markdown 预览场景。
 */
function resolveConclusionMarkdownScope(
  chat: CHAT.ChatItem,
  deepThink: boolean
): MarkdownNormalizationScope {
  const structuredConversation = isStructuredConversation(
    chat.agentType,
    deepThink
  );
  return chat.conclusion && structuredConversation
    ? "structured_summary"
    : "default";
}

const ConclusionSection: FC<{
  chat: CHAT.ChatItem;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
  normalizationScope: MarkdownNormalizationScope;
}> = ({ chat, changeFile, normalizationScope }) => {
  const summary = resolveTaskSummaryText(chat.conclusion) || "任务已完成";
  const summaryStreaming =
    !!chat.loading && chat.conclusion?.messageType === "agent_stream";
  const attachmentFiles = useMemo(
    () => getTaskFiles(chat.conclusion),
    [chat.conclusion]
  );
  return (
    <div className="mb-[8px]">
      <div className="mb-[8px] rounded-2xl bg-white/72 px-1 py-1">
        <MarkdownRenderer
          markDownContent={summary}
          isStreaming={summaryStreaming}
          normalizationScope={normalizationScope}
          className="text-[15px] leading-8"
        />
      </div>
      <AttachmentList
        files={attachmentFiles}
        preview={true}
        review={(file) => changeFile?.(file, chat)}
      />
    </div>
  );
};

const ThinkingMessage: FC = () => (
  <div className="mt-6 flex w-full justify-start">
    <Message from="assistant" className="w-full max-w-full">
      <MessageContent>
        <div className="flex items-center text-[15px] font-medium text-muted-foreground">
          <span className="thinking-shimmer text-[15px] font-medium tracking-[0.02em]">Thinking</span>
        </div>
      </MessageContent>
    </Message>
  </div>
);

const DialogueComponent: FC<Props> = (props) => {
  const { chat, streamingThought, deepThink, changeTask, changeFile, changePlan, onRegenerate } = props;
  const isPlanSolveMessage = isPlanSolveConversation(chat.agentType, deepThink);
  const isReactType = !isPlanSolveMessage;
  const plannerRounds = useMemo(
    () => buildPlannerRoundsForDisplay(chat, streamingThought),
    [chat, streamingThought]
  );
  const [thoughtVersionIndex, setThoughtVersionIndex] = useState(() =>
    Math.max(plannerRounds.length - 1, 0)
  );
  const [planVersionIndex, setPlanVersionIndex] = useState(() =>
    Math.max(plannerRounds.length - 1, 0)
  );
  const previousRoundCountRef = useRef(plannerRounds.length);
  useEffect(() => {
    const previousCount = previousRoundCountRef.current;
    const nextCount = plannerRounds.length;
    setThoughtVersionIndex((current) =>
      syncPlannerVersionCursor(current, previousCount, nextCount)
    );
    setPlanVersionIndex((current) =>
      syncPlannerVersionCursor(current, previousCount, nextCount)
    );
    previousRoundCountRef.current = nextCount;
  }, [plannerRounds.length]);
  const latestRoundIndex = Math.max(plannerRounds.length - 1, 0);
  const selectedThoughtRound = plannerRounds[thoughtVersionIndex];
  const selectedPlanRound = plannerRounds[planVersionIndex];
  const thoughtText = selectedThoughtRound?.planThought || "";
  const displayedPlan = selectedPlanRound?.plan || chat.plan;
  const thoughtVersionLabel =
    plannerRounds.length > 1
      ? `${thoughtVersionIndex + 1}/${plannerRounds.length}`
      : undefined;
  const planVersionLabel =
    plannerRounds.length > 1
      ? `${planVersionIndex + 1}/${plannerRounds.length}`
      : undefined;
  const planIsHistoricalSnapshot = planVersionIndex < latestRoundIndex;
  const conclusionMarkdownScope = resolveConclusionMarkdownScope(chat, deepThink);
  const hasAssistantPayload =
    !!chat.response ||
    !!thoughtText ||
    !!chat.tip ||
    !!displayedPlan ||
    !!chat.tasks.length ||
    !!chat.conclusion;
  const showStandaloneResponse =
    chat.agentType === 0 && !!chat.response && !chat.conclusion;
  const [copied, setCopied] = useState(false);

  const changeActiveChat = useCallback((task: CHAT.Task, targetChat: CHAT.ChatItem) => {
    changeTask?.(task, targetChat);
  }, [changeTask]);

  const handleCopy = useCallback(() => {
    if (!chat.response) return;
    navigator.clipboard.writeText(
      normalizeMarkdownForDisplay(chat.response, { scope: "default" })
    ).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [chat.response]);

  return (
    <div className="flex h-full flex-col text-[15px] font-normal text-[#111827]">
      {/* 附件 */}
      {(chat.files || []).length ? (
        <div className="mt-6 flex w-full justify-end">
          <AttachmentList files={chat.files} preview={false} />
        </div>
      ) : null}

      {/* 用户消息 */}
      {chat.query ? (
        <div className="mt-6 flex w-full justify-end">
          <Message from="user" className="max-w-[82%]">
            <MessageContent>
              {chat.query}
            </MessageContent>
          </Message>
        </div>
      ) : null}

      {/* 提示 */}
      {chat.tip ? (
        <div className="mt-5 w-full text-[15px] text-muted-foreground">
          {chat.tip}
        </div>
      ) : null}

      <div className="mt-5 w-full">
        <RunStatus
          status={chat.metrics?.status}
          finishedAt={chat.finishedAt}
        />
      </div>

      {/* AI 回复（Markdown） */}
      {showStandaloneResponse ? (
        <div className="mt-6 flex w-full justify-start">
          <Message from="assistant" className="w-full max-w-full">
            <MessageContent>
              <MarkdownRenderer
                markDownContent={chat.response}
                isStreaming={chat.loading}
                normalizationScope="default"
              />
            </MessageContent>
            {!chat.loading ? (
              <MessageActions className="mt-2">
                <MessageAction tooltip="复制" onClick={handleCopy}>
                  {copied
                    ? <CheckIcon className="size-4" />
                    : <CopyIcon className="size-4" />}
                </MessageAction>
                <MessageAction tooltip="重新生成" onClick={onRegenerate} disabled={!onRegenerate}>
                  <RefreshCwIcon className="size-4" />
                </MessageAction>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <MessageAction tooltip="更多">
                      <MoreHorizontalIcon className="size-4" />
                    </MessageAction>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start">
                    <DropdownMenuItem onClick={handleCopy}>复制原文</DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </MessageActions>
            ) : null}
          </Message>
        </div>
      ) : null}

      {/* AI 思考中占位 */}
      {chat.loading && !hasAssistantPayload ? <ThinkingMessage /> : null}

      {/* 思考过程（深度研究模式） */}
      {!isReactType && thoughtText ? (
        <div className="mt-6 w-full overflow-hidden rounded-2xl border border-[var(--chat-border)]/18 bg-[var(--chat-surface-soft)]/40 p-3 shadow-[var(--shadow-sm)] ring-0">
          <div className="mb-2 flex items-center justify-between gap-3">
            <div className="text-[12px] font-medium text-[var(--chat-text-muted)]">
              Planner Thought
            </div>
            {thoughtVersionLabel ? (
              <div className="inline-flex items-center gap-1 rounded-full bg-[var(--chat-surface)] px-2 py-1 text-[11px] font-medium text-[var(--chat-text-soft)]">
                <button
                  type="button"
                  className="rounded px-1 disabled:opacity-40"
                  onClick={() => setThoughtVersionIndex((current) => Math.max(current - 1, 0))}
                  disabled={thoughtVersionIndex <= 0}
                >
                  {"<"}
                </button>
                <span>{thoughtVersionLabel}</span>
                <button
                  type="button"
                  className="rounded px-1 disabled:opacity-40"
                  onClick={() => setThoughtVersionIndex((current) => Math.min(current + 1, latestRoundIndex))}
                  disabled={thoughtVersionIndex >= latestRoundIndex}
                >
                  {">"}
                </button>
              </div>
            ) : null}
          </div>
          <Reasoning
            isStreaming={chat.loading && thoughtVersionIndex === latestRoundIndex}
            defaultOpen
            className="not-prose mb-0"
          >
            <ReasoningTrigger className="rounded-xl px-2 py-1.5 hover:bg-[var(--chat-surface-muted)]/32" />
            <ReasoningContent>{thoughtText}</ReasoningContent>
          </Reasoning>
        </div>
      ) : null}

      {/* 任务计划 */}
      {!isReactType && displayedPlan ? (
        <div className="mt-6 w-full">
          <PlanSection
            plan={displayedPlan}
            versionLabel={planVersionLabel}
            onPrev={() => setPlanVersionIndex((current) => Math.max(current - 1, 0))}
            onNext={() => setPlanVersionIndex((current) => Math.min(current + 1, latestRoundIndex))}
            canPrev={planVersionIndex > 0}
            canNext={planVersionIndex < latestRoundIndex}
            staticSnapshot={planIsHistoricalSnapshot}
          />
        </div>
      ) : null}

      {/* 任务时间线 */}
      {chat.tasks.length ? (
        <div className="mt-6 w-full">
          <TimeLine
            chat={chat}
            isPlanSolveMessage={isPlanSolveMessage}
            changeActiveChat={changeActiveChat}
            changePlan={changePlan}
            changeFile={changeFile}
          />
        </div>
      ) : null}

      {/* 结论 */}
      {chat.conclusion ? (
        <div className="w-full">
          <ConclusionSection
            chat={chat}
            changeFile={changeFile}
            normalizationScope={conclusionMarkdownScope}
          />
        </div>
      ) : null}

    </div>
  );
};

const Dialogue = memo(
  DialogueComponent,
  (prev, next) =>
    prev.chat === next.chat &&
    prev.deepThink === next.deepThink &&
    prev.streamingThought === next.streamingThought &&
    prev.changeTask === next.changeTask &&
    prev.changeFile === next.changeFile &&
    prev.changePlan === next.changePlan &&
    prev.onRegenerate === next.onRegenerate
);

export default Dialogue;
