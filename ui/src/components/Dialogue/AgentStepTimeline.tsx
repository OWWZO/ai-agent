import {
  FC,
  memo,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { motion, AnimatePresence } from "motion/react";
import {
  BrainIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  FilePenLineIcon,
  FileTextIcon,
  GlobeIcon,
  LoaderCircleIcon,
  SearchIcon,
  SparklesIcon,
  TerminalIcon,
  BotIcon,
  WrenchIcon,
  MessageCircleQuestionIcon,
  CodeIcon,
  PackageIcon,
} from "lucide-react";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Reasoning,
  ReasoningContent,
  ReasoningTrigger,
} from "@/components/ai-elements/reasoning";
import {
  resolveDeepSearchStage,
  shouldRenderDeepSearchPreview,
} from "@/utils/deepSearch";
import { cn } from "@/lib/utils";
import { DeepSearchPreviewItem, ToolItem } from "./Timeline";
import {
  deriveAgentProcessModel,
  type ProcessSegment,
  type ProcessStepGroup,
  type ProcessStepKind,
  type ProcessStepRow,
} from "./agentProcessModel";
import UserBriefCard from "./UserBriefCard";
import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import { resolveTaskSummaryText } from "./contentHelpers";

type AgentStepTimelineProps = {
  chat: CHAT.ChatItem;
  isPlanSolveMessage: boolean;
  thoughtText?: string;
  thoughtStreaming?: boolean;
  thoughtVersionLabel?: string;
  thoughtVersionIndex?: number;
  thoughtVersionTotal?: number;
  onThoughtPrev?: () => void;
  onThoughtNext?: () => void;
  canThoughtPrev?: boolean;
  canThoughtNext?: boolean;
  planSlot?: ReactNode;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
};

const RICH_INLINE_TYPES = new Set([
  "ask_user_question",
  "plan_approval",
  "session_tasks",
  "ui_tree",
  "ui_patch",
  "browser",
  "task_summary",
]);

function asText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeComparableText(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

/** 与底部 conclusion 同文案的时间线条目视为重复（终答不当思考/过程） */
function isDuplicateOfConclusion(
  segment: ProcessSegment,
  conclusionText: string
): boolean {
  const normalizedConclusion = normalizeComparableText(conclusionText);
  if (!normalizedConclusion) {
    return false;
  }
  if (segment.type === "final_reply" || segment.type === "assistant_reply") {
    return (
      normalizeComparableText(segment.text) === normalizedConclusion
    );
  }
  if (segment.type === "thinking") {
    const thought = asText(segment.step.tool.toolThought);
    return normalizeComparableText(thought) === normalizedConclusion;
  }
  return false;
}

function isRichInlineStep(step: ProcessStepRow): boolean {
  const messageType = step.tool.messageType || "";
  if (RICH_INLINE_TYPES.has(messageType)) {
    return true;
  }
  if (step.kind === "agent" || Boolean(step.tool.children?.length)) {
    return true;
  }
  if (messageType === "deep_search") {
    return true;
  }
  return false;
}

const stepKindIcon = (kind: ProcessStepKind, active: boolean) => {
  const className = cn(
    "size-3.5 shrink-0",
    active ? "text-[var(--chat-accent)]" : "text-[var(--chat-text-muted)]"
  );
  switch (kind) {
    case "thinking":
      return active ? (
        <SparklesIcon className={cn(className, "text-[var(--chat-accent)]")} />
      ) : (
        <BrainIcon className={className} />
      );
    case "read":
      return <FileTextIcon className={className} />;
    case "edit":
    case "file":
      return <FilePenLineIcon className={className} />;
    case "terminal":
      return <TerminalIcon className={className} />;
    case "search":
      return <SearchIcon className={className} />;
    case "browser":
      return <GlobeIcon className={className} />;
    case "code":
      return <CodeIcon className={className} />;
    case "agent":
      return <BotIcon className={className} />;
    case "interactive":
      return <MessageCircleQuestionIcon className={className} />;
    case "user_message":
      return <MessageCircleQuestionIcon className={className} />;
    case "artifact":
      return <PackageIcon className={className} />;
    default:
      return active ? (
        <LoaderCircleIcon className={cn(className, "animate-spin")} />
      ) : (
        <WrenchIcon className={className} />
      );
  }
};

const DurationBadge: FC<{ label?: string; active?: boolean }> = ({
  label,
  active,
}) => {
  if (!label && !active) {
    return null;
  }
  return (
    <span
      className={cn(
        "timeline-duration ml-auto shrink-0 text-[12px]",
        active
          ? "text-[var(--chat-accent-muted)]"
          : "text-[var(--chat-text-muted)]/80"
      )}
    >
      {active && !label ? "…" : label}
    </span>
  );
};

/** 视频同款：sparkle + 标签 + 右侧时长 */
const ThinkingRowHeader: FC<{
  streaming: boolean;
  durationLabel?: string;
  label?: string;
}> = ({ streaming, durationLabel, label = "深度思考" }) => (
  <div className="flex w-full min-w-0 items-center gap-2">
    <div className="flex size-5 shrink-0 items-center justify-center">
      {streaming ? (
        <SparklesIcon className="size-3.5 text-[var(--chat-accent)] motion-safe:animate-pulse" />
      ) : (
        <SparklesIcon className="size-3.5 text-[var(--chat-text-muted)]/75" />
      )}
    </div>
    <span className="timeline-thinking-label text-[13px]">{label}</span>
    {streaming ? (
      <span className="text-[12px] text-[var(--chat-text-muted)]">思考中</span>
    ) : null}
    <DurationBadge label={durationLabel} active={streaming} />
  </div>
);

const ThoughtHeader: FC<{
  streaming: boolean;
  durationLabel?: string;
  versionLabel?: string;
  onPrev?: () => void;
  onNext?: () => void;
  canPrev?: boolean;
  canNext?: boolean;
}> = ({
  streaming,
  durationLabel,
  versionLabel,
  onPrev,
  onNext,
  canPrev,
  canNext,
}) => (
  <div className="flex w-full items-center gap-2 text-[13px] text-[var(--chat-text-soft)]">
    <div className="flex size-5 shrink-0 items-center justify-center">
      {streaming ? (
        <SparklesIcon className="size-3.5 text-[var(--chat-accent)]" />
      ) : (
        <BrainIcon className="size-3.5 text-[var(--chat-text-muted)]" />
      )}
    </div>
    <span className="font-medium text-[var(--chat-text)]">深度思考</span>
    {streaming ? (
      <span className="inline-flex items-center gap-1 text-[12px] text-[var(--chat-text-muted)]">
        <span className="h-1 w-1 rounded-full bg-[var(--chat-accent)]/70 motion-safe:animate-pulse" />
        思考中
      </span>
    ) : null}
    {versionLabel ? (
      <div
        className="inline-flex items-center gap-0.5 rounded-full bg-[var(--chat-surface-soft)] px-1.5 py-0.5 text-[11px] text-[var(--chat-text-muted)]"
        onClick={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          className="rounded px-0.5 disabled:opacity-30"
          onClick={(event) => {
            event.stopPropagation();
            onPrev?.();
          }}
          disabled={!canPrev}
          aria-label="上一版思考"
        >
          {"<"}
        </button>
        <span>{versionLabel}</span>
        <button
          type="button"
          className="rounded px-0.5 disabled:opacity-30"
          onClick={(event) => {
            event.stopPropagation();
            onNext?.();
          }}
          disabled={!canNext}
          aria-label="下一版思考"
        >
          {">"}
        </button>
      </div>
    ) : null}
    <DurationBadge label={durationLabel} active={streaming} />
  </div>
);

type StepRowContext = {
  chat: CHAT.ChatItem;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
};

const CompactStepRow: FC<{
  step: ProcessStepRow;
  ctx: StepRowContext;
}> = memo(({ step, ctx }) => {
  const [open, setOpen] = useState(() => step.active && step.kind === "thinking");
  const isThought = step.kind === "thinking";
  const thoughtText = asText(step.tool.toolThought);
  const rich = isRichInlineStep(step);

  useEffect(() => {
    if (step.active && isThought) {
      setOpen(true);
    } else if (!step.active && isThought && thoughtText) {
      setOpen(false);
    }
  }, [step.active, isThought, thoughtText]);

  // 组内仅原生 CoT；折叠头与视频一致（时长在右侧，不写「已思考 N 秒」）
  if (isThought) {
    return (
      <div className="timeline-segment-enter py-0.5" data-testid="llm-reasoning-step">
        <Reasoning
          isStreaming={step.active}
          open={open}
          onOpenChange={setOpen}
          duration={
            step.durationMs != null
              ? Math.max(1, Math.round(step.durationMs / 1000))
              : undefined
          }
          className="not-prose mb-0"
        >
          <ReasoningTrigger
            className="m-0 rounded-lg px-1 py-1.5 hover:bg-[var(--chat-interactive-hover)]"
            getThinkingMessage={(streaming) => (
              <ThinkingRowHeader
                streaming={streaming}
                durationLabel={step.durationLabel}
              />
            )}
          />
          <ReasoningContent className="pl-7 text-[13px] leading-6 text-[var(--chat-text-soft)]">
            {thoughtText || "…"}
          </ReasoningContent>
        </Reasoning>
      </div>
    );
  }

  if (step.kind === "assistant_reply") {
    const text = thoughtText;
    if (!text) {
      return null;
    }
    return (
      <div className="timeline-assistant-reply timeline-segment-enter px-1 py-1 pl-7">
        {text}
      </div>
    );
  }

  if (rich) {
    const stage =
      step.tool.messageType === "deep_search"
        ? resolveDeepSearchStage(step.tool.resultMap?.messageType)
        : undefined;
    const showDeepSearchPreview =
      step.tool.messageType === "deep_search" &&
      shouldRenderDeepSearchPreview(stage);

    return (
      <div className="relative py-0.5">
        <div className="pointer-events-none absolute right-1 top-3 z-[1]">
          <DurationBadge label={step.durationLabel} active={step.active} />
        </div>
        {showDeepSearchPreview ? (
          <DeepSearchPreviewItem
            tool={step.tool}
            chat={ctx.chat}
            changeActiveChat={ctx.changeActiveChat}
          />
        ) : (
          <ToolItem
            tool={step.tool}
            chat={ctx.chat}
            changePlan={ctx.changePlan}
            changeActiveChat={ctx.changeActiveChat}
            changeFile={ctx.changeFile}
          />
        )}
      </div>
    );
  }

  return (
    <button
      type="button"
      className={cn(
        "timeline-tool-row group flex w-full items-center gap-2 rounded-md px-1 py-1.5 text-left",
        step.active ? "bg-[var(--chat-accent-soft)]/30" : ""
      )}
      onClick={() => {
        if (step.tool.messageType === "plan") {
          ctx.changePlan?.();
          return;
        }
        ctx.changeActiveChat(step.tool, ctx.chat);
      }}
    >
      <div className="flex size-5 shrink-0 items-center justify-center text-[var(--chat-text-muted)]">
        {step.active ? (
          <LoaderCircleIcon className="size-3.5 animate-spin text-[var(--chat-accent)]" />
        ) : (
          stepKindIcon(step.kind, step.active)
        )}
      </div>
      <div className="min-w-0 flex-1 overflow-hidden">
        <div className="flex min-w-0 items-baseline gap-2">
          <span
            className={cn(
              "truncate text-[13px] leading-5 tracking-[-0.01em]",
              step.active
                ? "font-medium text-[var(--chat-text)]"
                : "text-[var(--chat-text-soft)]/90"
            )}
          >
            {step.title}
          </span>
          {step.detail ? (
            <span className="truncate text-[12px] text-[var(--chat-text-muted)]/75">
              {step.detail}
            </span>
          ) : null}
        </div>
      </div>
      {step.expandable ? (
        <ChevronRightIcon className="size-3 shrink-0 text-[var(--chat-text-muted)]/60 opacity-0 transition-opacity group-hover:opacity-100" />
      ) : null}
      <DurationBadge label={step.durationLabel} active={step.active} />
    </button>
  );
});

CompactStepRow.displayName = "CompactStepRow";

const StepGroupBlock: FC<{
  group: ProcessStepGroup;
  defaultOpen: boolean;
  forceOpen: boolean;
  ctx: StepRowContext;
}> = memo(({ group, defaultOpen, forceOpen, ctx }) => {
  const [open, setOpen] = useState(defaultOpen);
  const canCollapse = group.collapsible !== false && group.stepCount > 0;

  useEffect(() => {
    if (forceOpen || group.active) {
      setOpen(true);
    } else if (canCollapse && group.completed && group.stepCount > 1) {
      setOpen(false);
    }
  }, [forceOpen, group.active, group.completed, group.stepCount, canCollapse]);

  const stepsBody = (
    <div className="timeline-rail relative ml-[10px] pl-3">
      <AnimatePresence initial={false}>
        {group.steps.map((step, index) => (
          <motion.div
            key={step.id}
            initial={{ opacity: 0, y: 5 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{
              duration: 0.2,
              delay: Math.min(index * 0.03, 0.18),
              ease: [0.22, 1, 0.36, 1],
            }}
          >
            <CompactStepRow step={step} ctx={ctx} />
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );

  if (!canCollapse) {
    return <div className="timeline-segment-enter w-full">{stepsBody}</div>;
  }

  return (
    <Collapsible
      open={open}
      onOpenChange={setOpen}
      className="timeline-segment-enter w-full"
    >
      <CollapsibleTrigger
        className={cn(
          "group flex w-full items-center gap-2 rounded-md px-1 py-1.5 text-left transition-colors",
          "hover:bg-[var(--chat-interactive-hover)]"
        )}
      >
        <div className="flex size-5 shrink-0 items-center justify-center">
          {group.active ? (
            <SparklesIcon className="size-3.5 text-[var(--chat-accent)] motion-safe:animate-pulse" />
          ) : (
            <SparklesIcon className="size-3.5 text-[var(--chat-text-muted)]/70" />
          )}
        </div>
        <span className="timeline-group-title min-w-0 flex-1 truncate text-[13px]">
          {group.title}
        </span>
        {group.digitalEmployee ? (
          <span className="hidden shrink-0 rounded-md bg-[var(--chat-surface-soft)] px-1.5 py-0.5 text-[11px] text-[var(--chat-text-muted)] sm:inline">
            {group.digitalEmployee}
          </span>
        ) : null}
        <motion.div
          animate={{ rotate: open ? 180 : 0 }}
          transition={{ duration: 0.2, ease: [0.25, 0.46, 0.45, 0.94] }}
          className="shrink-0"
        >
          <ChevronDownIcon className="size-3.5 text-[var(--chat-text-muted)]/70" />
        </motion.div>
        <DurationBadge label={group.durationLabel} active={group.active} />
      </CollapsibleTrigger>

      <CollapsibleContent>{stepsBody}</CollapsibleContent>
    </Collapsible>
  );
});

StepGroupBlock.displayName = "StepGroupBlock";

const UserMessageSegment: FC<{ step: ProcessStepRow }> = memo(({ step }) => (
  <div className="my-2 w-full" data-testid="process-user-message">
    <UserBriefCard tool={step.tool} />
  </div>
));

UserMessageSegment.displayName = "UserMessageSegment";

/** 折叠「深度思考」：默认收起；视频同款 sparkle + 右侧时长 */
const ThinkingSegment: FC<{ step: ProcessStepRow }> = memo(({ step }) => {
  const [open, setOpen] = useState(false);
  const thoughtText = asText(step.tool.toolThought);
  const streaming = step.active;

  useEffect(() => {
    if (streaming) {
      setOpen(true);
    } else if (thoughtText) {
      setOpen(false);
    }
  }, [streaming, thoughtText]);

  return (
    <div className="timeline-segment-enter py-0.5" data-testid="process-thinking">
      <Reasoning
        isStreaming={streaming}
        open={open}
        onOpenChange={setOpen}
        duration={
          step.durationMs != null
            ? Math.max(1, Math.round(step.durationMs / 1000))
            : undefined
        }
        className="not-prose mb-0"
      >
        <ReasoningTrigger
          className="m-0 rounded-md px-1 py-1.5 hover:bg-[var(--chat-interactive-hover)]"
          getThinkingMessage={(isStreaming) => (
            <ThinkingRowHeader
              streaming={isStreaming}
              durationLabel={step.durationLabel}
            />
          )}
        />
        <ReasoningContent className="pl-7 text-[13px] leading-6 text-[var(--chat-text-soft)]">
          {thoughtText || "…"}
        </ReasoningContent>
      </Reasoning>
    </div>
  );
});

ThinkingSegment.displayName = "ThinkingSegment";

/** 助手过程回复：深度思考下方常显 */
const AssistantReplySegment: FC<{ text: string; streaming?: boolean }> = memo(
  ({ text, streaming }) => (
    <div
      className="timeline-assistant-reply timeline-segment-enter mt-0.5 mb-2 w-full px-1 pl-7"
      data-testid="process-assistant-reply"
    >
      <MarkdownRenderer
        markDownContent={text}
        isStreaming={Boolean(streaming)}
        className="chat-markdown text-[15px] leading-[1.75] tracking-[-0.01em] text-[var(--chat-text)]"
      />
    </div>
  )
);

AssistantReplySegment.displayName = "AssistantReplySegment";

const FinalReplySegment: FC<{ text: string }> = memo(({ text }) => (
  <div
    className="timeline-segment-enter mt-3 w-full px-1"
    data-testid="process-final-reply"
  >
    <MarkdownRenderer
      markDownContent={text}
      className="chat-markdown conclusion-markdown text-[15px] leading-[1.75] tracking-[-0.01em] text-[var(--chat-text)]"
    />
  </div>
));

FinalReplySegment.displayName = "FinalReplySegment";

const ProcessSegmentView: FC<{
  segment: ProcessSegment;
  isLast: boolean;
  loading: boolean;
  ctx: StepRowContext;
}> = memo(({ segment, isLast, loading, ctx }) => {
  if (segment.type === "thinking") {
    return <ThinkingSegment step={segment.step} />;
  }
  if (segment.type === "assistant_reply") {
    return (
      <AssistantReplySegment
        text={segment.text}
        streaming={loading && isLast && segment.step.active}
      />
    );
  }
  if (segment.type === "user_message") {
    return <UserMessageSegment step={segment.step} />;
  }
  if (segment.type === "final_reply") {
    return <FinalReplySegment text={segment.text} />;
  }
  // 单步工具：直接展开行（截图 `>_ tool`），多步才折叠「执行了 N 个步骤」
  if (segment.group.stepCount <= 1 && !segment.group.active) {
    return (
      <div className="w-full">
        {segment.group.steps.map((step) => (
          <CompactStepRow key={step.id} step={step} ctx={ctx} />
        ))}
      </div>
    );
  }
  return (
    <StepGroupBlock
      group={segment.group}
      defaultOpen={
        segment.group.active ||
        !segment.group.completed ||
        segment.group.stepCount <= 2
      }
      forceOpen={segment.group.active || (loading && isLast)}
      ctx={ctx}
    />
  );
});

ProcessSegmentView.displayName = "ProcessSegmentView";

/**
 * 产品级 Agent 过程时间线：
 * 深度思考 → 意图句 → 可折叠步骤组（左轨竖线 + 图标 + 耗时）
 * 富交互步骤内联复用 Timeline.ToolItem，避免能力回退。
 */
const AgentStepTimelineComponent: FC<AgentStepTimelineProps> = (props) => {
  const {
    chat,
    isPlanSolveMessage,
    thoughtText = "",
    thoughtStreaming = false,
    thoughtVersionLabel,
    thoughtVersionIndex = 0,
    thoughtVersionTotal = 0,
    onThoughtPrev,
    onThoughtNext,
    canThoughtPrev,
    canThoughtNext,
    planSlot,
    changeActiveChat,
    changePlan,
    changeFile,
  } = props;

  const model = useMemo(
    () =>
      deriveAgentProcessModel({
        chat,
        isPlanSolve: isPlanSolveMessage,
        thoughtText,
        thoughtStreaming,
        thoughtVersionLabel,
        thoughtVersionIndex,
        thoughtVersionTotal,
      }),
    [
      chat,
      isPlanSolveMessage,
      thoughtText,
      thoughtStreaming,
      thoughtVersionLabel,
      thoughtVersionIndex,
      thoughtVersionTotal,
    ]
  );

  const [thoughtOpen, setThoughtOpen] = useState(() =>
    Boolean(thoughtStreaming || chat.loading)
  );

  useEffect(() => {
    if (thoughtStreaming || (chat.loading && thoughtText)) {
      setThoughtOpen(true);
    } else if (!chat.loading && thoughtText) {
      setThoughtOpen(false);
    }
  }, [thoughtStreaming, chat.loading, thoughtText]);

  const ctx = useMemo<StepRowContext>(
    () => ({
      chat,
      changeActiveChat,
      changePlan,
      changeFile,
    }),
    [chat, changeActiveChat, changePlan, changeFile]
  );

  const handleThoughtOpenChange = useCallback((open: boolean) => {
    setThoughtOpen(open);
  }, []);

  if (!model.hasProcess && !planSlot) {
    return null;
  }

  return (
    <div
      className="agent-step-timeline w-full max-w-[min(960px,100%)]"
      data-testid="agent-step-timeline"
      aria-label="Agent 执行过程"
    >
      {model.thought ? (
        <div className="timeline-segment-enter mb-2">
          <Reasoning
            isStreaming={model.thought.streaming}
            open={thoughtOpen}
            onOpenChange={handleThoughtOpenChange}
            duration={
              model.thought.durationMs != null
                ? Math.max(1, Math.round(model.thought.durationMs / 1000))
                : undefined
            }
            className="not-prose mb-0"
          >
            <ReasoningTrigger className="m-0 rounded-md px-1 py-1.5 hover:bg-[var(--chat-interactive-hover)]">
              {model.thought.versionLabel ? (
                <ThoughtHeader
                  streaming={model.thought.streaming}
                  durationLabel={model.thought.durationLabel}
                  versionLabel={model.thought.versionLabel}
                  onPrev={onThoughtPrev}
                  onNext={onThoughtNext}
                  canPrev={canThoughtPrev}
                  canNext={canThoughtNext}
                />
              ) : (
                <ThinkingRowHeader
                  streaming={model.thought.streaming}
                  durationLabel={model.thought.durationLabel}
                />
              )}
            </ReasoningTrigger>
            <ReasoningContent className="pl-7 text-[13px] leading-6 text-[var(--chat-text-soft)]">
              {model.thought.text}
            </ReasoningContent>
          </Reasoning>

          {!thoughtOpen && model.intentLine ? (
            <p className="timeline-assistant-reply mt-1 pl-7 text-[13px] leading-6">
              {model.intentLine}
            </p>
          ) : null}
        </div>
      ) : null}

      {planSlot ? <div className="mb-3 w-full">{planSlot}</div> : null}

      {model.segments.length ? (
        <div className="flex flex-col gap-1">
          {model.segments.map((segment, index) => {
            const conclusionText = resolveTaskSummaryText(chat.conclusion);
            // 终答区已有 conclusion：时间线里不再重复同文案（含误标成思考/过程回复）
            if (conclusionText && isDuplicateOfConclusion(segment, conclusionText)) {
              return null;
            }
            const key =
              segment.type === "group"
                ? segment.group.id
                : segment.type === "thinking"
                  ? `think-${segment.step.id}`
                  : segment.type === "assistant_reply"
                    ? `reply-${segment.step.id}`
                    : segment.type === "user_message"
                      ? `msg-${segment.step.id}`
                      : `final-${segment.step.id}`;
            return (
              <ProcessSegmentView
                key={key}
                segment={segment}
                isLast={index === model.segments.length - 1}
                loading={chat.loading}
                ctx={ctx}
              />
            );
          })}
        </div>
      ) : null}
    </div>
  );
};

export const AgentStepTimeline = memo(
  AgentStepTimelineComponent,
  (prev, next) =>
    prev.chat === next.chat &&
    prev.isPlanSolveMessage === next.isPlanSolveMessage &&
    prev.thoughtText === next.thoughtText &&
    prev.thoughtStreaming === next.thoughtStreaming &&
    prev.thoughtVersionLabel === next.thoughtVersionLabel &&
    prev.thoughtVersionIndex === next.thoughtVersionIndex &&
    prev.thoughtVersionTotal === next.thoughtVersionTotal &&
    prev.planSlot === next.planSlot &&
    prev.changeActiveChat === next.changeActiveChat &&
    prev.changePlan === next.changePlan &&
    prev.changeFile === next.changeFile &&
    prev.onThoughtPrev === next.onThoughtPrev &&
    prev.onThoughtNext === next.onThoughtNext &&
    prev.canThoughtPrev === next.canThoughtPrev &&
    prev.canThoughtNext === next.canThoughtNext
);

AgentStepTimeline.displayName = "AgentStepTimeline";

export default AgentStepTimeline;
