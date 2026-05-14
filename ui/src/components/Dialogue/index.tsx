import { FC, useState, useCallback, useMemo, memo, useEffect, useRef } from "react";
import AttachmentList from "@/components/AttachmentList";
import { getTaskFiles } from "@/utils/taskArtifacts";
import {
  Message,
  MessageContent,
} from "@/components/ai-elements/message";
import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import {
  Reasoning,
  ReasoningTrigger,
  ReasoningContent,
} from "@/components/ai-elements/reasoning";
import ThinkingMessage from "./ThinkingMessage";
import {
} from "lucide-react";
import {
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
import { PlanSection } from "./PlanSection";
import { Timeline } from "./Timeline";
import { MessageToolbar } from "./MessageToolbar";
import { resolveTaskSummaryText } from "./contentHelpers";

type Props = {
  chat: CHAT.ChatItem;
  streamingThought?: string;
  deepThink: boolean;
  changeTask?: (task: CHAT.Task, chat?: CHAT.ChatItem) => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
  changePlan?: () => void;
  onRegenerate?: () => void;
};

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
          className="conclusion-markdown text-[15px] leading-8"
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

  const changeActiveChat = useCallback((task: CHAT.Task, targetChat: CHAT.ChatItem) => {
    changeTask?.(task, targetChat);
  }, [changeTask]);

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
              <MessageToolbar
                response={chat.response}
                onRegenerate={onRegenerate}
              />
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
          <Timeline
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
