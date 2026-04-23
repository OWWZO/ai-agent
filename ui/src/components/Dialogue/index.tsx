import { FC, useState, useCallback, useMemo, memo } from "react";
import { motion } from "motion/react";
import AttachmentList from "@/components/AttachmentList";
import LoadingSpinner from "@/components/LoadingSpinner";
import { buildAction, getIcon } from "@/utils/chat";
import { getTaskFiles } from "@/utils/historyArtifacts";
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
} from "lucide-react";
import {
  normalizeMarkdownForDisplay,
  type MarkdownNormalizationScope,
} from "@/utils/markdown";

type Props = {
  chat: CHAT.ChatItem;
  streamingThought?: string;
  deepThink: boolean;
  changeTask?: (task: CHAT.Task) => void;
  changeFile?: (file: CHAT.TFile) => void;
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

const PlanSection: FC<{ plan?: CHAT.Plan }> = memo(({ plan }) => {
  const normalizedPlan = useMemo(() => normalizePlanForDisplay(plan), [plan]);

  if (!normalizedPlan || !normalizedPlan.stages.length) {
    return null;
  }

  const completedCount = normalizedPlan.stepStatus.filter(
    (status) => status === "completed"
  ).length;

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.24, ease: [0.25, 0.46, 0.45, 0.94] }}
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
        <div className="shrink-0 rounded-full bg-[var(--chat-surface)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
          {completedCount}/{normalizedPlan.stages.length}
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
              initial={{ opacity: 0, x: -6 }}
              animate={{ opacity: 1, x: 0 }}
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
                    <span
                      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${tone.badgeClass}`}
                    >
                      <span className={`h-1.5 w-1.5 rounded-full ${tone.dotClass}`}></span>
                      {tone.label}
                    </span>
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
  changePlan?: () => void;
  changeActiveChat: (task: CHAT.Task) => void;
  changeFile?: (file: CHAT.TFile) => void;
}> = memo(({ tool, changePlan, changeActiveChat, changeFile }) => {
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
            review={changeFile}
          />
        </div>
      );
    }
    default: {
      const loadingType = ["html", "markdown", "data_analysis"];
      const loading =
        !tool.resultMap?.isFinal &&
        ((tool.messageType === "deep_search" &&
          (tool.resultMap.messageType === "extend" ||
            tool.resultMap.messageType === "report")) ||
          loadingType.includes(tool.messageType));
      const isSearching =
        tool.messageType === "deep_search" &&
        !tool.resultMap?.isFinal &&
        tool.resultMap?.messageType !== "report";
      const isSummarizing = tool.messageType === "deep_search" && tool.resultMap?.messageType === "report";
      const isDeepSearchInline = isSearching || isSummarizing;

      return (
        <div
          className={
            "mt-2 flex w-full max-w-full cursor-pointer items-center gap-3 rounded-xl px-1 py-2 transition-all duration-200 hover:bg-muted/35"
          }
          onClick={() => changeActiveChat(tool)}
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
  prevProps.changePlan === nextProps.changePlan &&
  prevProps.changeActiveChat === nextProps.changeActiveChat &&
  prevProps.changeFile === nextProps.changeFile
);

ToolItem.displayName = "ToolItem";

const TimeLineContent: FC<{
  tasks: CHAT.Task[];
  isReactType: boolean;
  changeActiveChat: (task: CHAT.Task) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ tasks, isReactType, changeActiveChat, changePlan, changeFile }) => (
  <>
    {tasks.map((t, i) => (
      <div key={t.id || t.messageId || t.taskId || i} className="overflow-hidden">
        {!isReactType && t.task ? <div className="font-[500]">{t.task}</div> : null}
        {(t.children || []).map((tool, j) => (
          <div key={tool.id || tool.messageId || tool.taskId || j}>
            <ToolItem
              tool={tool}
              changePlan={changePlan}
              changeActiveChat={changeActiveChat}
              changeFile={changeFile}
            />
          </div>
        ))}
      </div>
    ))}
  </>
);

const TimeLine: FC<{
  chat: CHAT.ChatItem;
  isReactType: boolean;
  changeActiveChat: (task: CHAT.Task) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ chat, isReactType, changeActiveChat, changePlan, changeFile }) => (
  <>
    {chat.tasks.map((t, i) => {
      const lastTask = i === chat.tasks.length - 1;
      const groupKey = t[0]?.id || t[0]?.messageId || t[0]?.taskId || i;
      return (
        <div className="flex w-full" key={groupKey}>
          {!isReactType ? (
            <div className="relative mb-2 mt-1 w-8 shrink-0 overflow-hidden">
              {lastTask && chat.loading ? (
                <LoadingSpinner/>
              ) : (
                <i className="font_family icon-yiwanchengtianchong absolute left-0 top-0 text-[16px] text-[#0071e3]"></i>
              )}
            </div>
          ) : null}
          <div className="mb-2 flex-1 overflow-hidden">
            <TimeLineContent
              tasks={t}
              isReactType={isReactType}
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
  const isStructuredConversation =
    chat.agentType === 1 || chat.agentType === 2 || deepThink;
  return chat.conclusion && isStructuredConversation
    ? "structured_summary"
    : "default";
}

const ConclusionSection: FC<{
  chat: CHAT.ChatItem;
  changeFile?: (file: CHAT.TFile) => void;
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
        review={changeFile}
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
  const isPlanSolveMessage = chat.agentType === 1 || deepThink;
  const isReactType = !isPlanSolveMessage;
  const thoughtText = streamingThought ?? chat.thought ?? "";
  const conclusionMarkdownScope = resolveConclusionMarkdownScope(chat, deepThink);
  const hasAssistantPayload =
    !!chat.response ||
    !!thoughtText ||
    !!chat.tip ||
    !!chat.plan ||
    !!chat.tasks.length ||
    !!chat.conclusion;
  const showStandaloneResponse =
    chat.agentType === 0 && !!chat.response && !chat.conclusion;
  const [copied, setCopied] = useState(false);

  const changeActiveChat = useCallback((task: CHAT.Task) => {
    changeTask?.(task);
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
          <Reasoning isStreaming={chat.loading} defaultOpen className="not-prose mb-0">
            <ReasoningTrigger className="rounded-xl px-2 py-1.5 hover:bg-[var(--chat-surface-muted)]/32" />
            <ReasoningContent>{thoughtText}</ReasoningContent>
          </Reasoning>
        </div>
      ) : null}

      {/* 任务计划 */}
      {!isReactType && chat.plan ? (
        <div className="mt-6 w-full">
          <PlanSection plan={chat.plan} />
        </div>
      ) : null}

      {/* 任务时间线 */}
      {chat.tasks.length ? (
        <div className="mt-6 w-full">
          <TimeLine
            chat={chat}
            isReactType={isReactType}
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
