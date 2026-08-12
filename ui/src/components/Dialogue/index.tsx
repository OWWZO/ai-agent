import { FC, useState, useCallback, useMemo, memo, useEffect, useRef, useSyncExternalStore } from "react";
import AttachmentList from "@/components/AttachmentList";
import { getTaskFiles } from "@/utils/taskArtifacts";
import {
  Message,
  MessageContent,
} from "@/components/ai-elements/message";
import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import { AnimatedOrb } from "@/components/chat/AnimatedOrb";
import ThinkingMessage from "./ThinkingMessage";
import RunPresenceBar from "./RunPresenceBar";
import RunStatus from "@/components/ActionView/RunStatus";
import { isPlanSolveConversation } from "@/utils/agentMode";
import { collectChatArtifactFiles } from "@/utils/markdownArtifacts";
import {
  buildPlannerRoundsForDisplay,
  syncPlannerVersionCursor,
} from "./plannerHistory";
import { PlanSection } from "./PlanSection";
import { AgentStepTimeline } from "./AgentStepTimeline";
import { MessageToolbar } from "./MessageToolbar";
import { resolveTaskSummaryText } from "./contentHelpers";
import GenUiInline from "@/components/genui/GenUiInline";
import { findFeaturedGenUi } from "@/utils/chat/genuiState";
import { applyUiPatches } from "@/components/genui/applyUiPatch";
import {
  genUiLocalScopeKey,
  getGenUiLocalSnapshot,
  getLocalUiPatches,
  getLocalUiVersion,
  subscribeGenUiLocalTree,
} from "@/components/genui/genUiLocalTreeStore";

type Props = {
  chat: CHAT.ChatItem;
  streamingThought?: string;
  deepThink: boolean;
  /** 会话级产物；终答 Markdown 相对引用优先用此表，避免只认本轮 */
  sessionArtifactFiles?: CHAT.TFile[];
  changeTask?: (task: CHAT.Task, chat?: CHAT.ChatItem) => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
  changePlan?: () => void;
  onRegenerate?: () => void;
};

const ConclusionSection: FC<{
  chat: CHAT.ChatItem;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
  sessionArtifactFiles?: CHAT.TFile[];
}> = ({ chat, changeFile, sessionArtifactFiles }) => {
  const summary = resolveTaskSummaryText(chat.conclusion) || "任务已完成";
  const summaryStreaming =
    !!chat.loading && chat.conclusion?.messageType === "agent_stream";
  const attachmentFiles = useMemo(
    () => getTaskFiles(chat.conclusion),
    [chat.conclusion]
  );
  // 终答相对文件名引用优先会话级产物表，否则回退本轮。
  const artifactFiles = useMemo(() => {
    if (sessionArtifactFiles?.length) {
      return sessionArtifactFiles;
    }
    return collectChatArtifactFiles(chat);
  }, [chat, sessionArtifactFiles]);
  return (
    <div className="mt-5">
      <div className="mb-3 px-1 py-1">
        <MarkdownRenderer
          markDownContent={summary}
          isStreaming={summaryStreaming}
          artifactFiles={artifactFiles}
          className="chat-markdown conclusion-markdown text-[15px] leading-[1.75] tracking-[-0.01em] text-[var(--chat-text)]"
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
  const {
    chat,
    streamingThought,
    deepThink,
    sessionArtifactFiles,
    changeTask,
    changeFile,
    changePlan,
    onRegenerate,
  } = props;
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
  // 顶部版本化思考仅用 plan_thought；原生 CoT / 过程文走时间线分段（避免重复）
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
  // multiAgent.tasks 原地 push/merge 时数组引用不变；必须以 chat 快照为依赖，
  // 每次 Dialogue 因 chat 引用更新而渲染时重算 featured（含 emit_ui_patch 重放）。
  const localGenUiTick = useSyncExternalStore(
    subscribeGenUiLocalTree,
    getGenUiLocalSnapshot,
    getGenUiLocalSnapshot
  );
  const localScopeKey = genUiLocalScopeKey(chat.sessionId, chat.requestId);
  const localVersion = getLocalUiVersion(localScopeKey);

  const featuredGenUi = useMemo(() => {
    const base = findFeaturedGenUi(chat.tasks, chat.multiAgent?.tasks);
    if (!base) return null;
    const localPatches = getLocalUiPatches(localScopeKey);
    if (!localPatches.length) {
      return {
        ...base,
        revision: `${base.revision}|loc0`,
      };
    }
    const tree = applyUiPatches(base.tree, localPatches);
    return {
      ...base,
      tree,
      patchCount: base.patchCount + localPatches.length,
      revision: `${base.revision}|loc${localVersion}:${localPatches.length}`,
    };
    // chat：流式 flush 会换新 chat 对象；localGenUiTick：客户端 patch_ui
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chat, localGenUiTick, localScopeKey, localVersion]);
  const hasAssistantPayload =
    !!chat.response ||
    !!thoughtText ||
    !!displayedPlan ||
    !!chat.tasks.length ||
    !!chat.conclusion ||
    !!featuredGenUi;
  const showStandaloneResponse =
    chat.agentType === 0 && !!chat.response && !chat.conclusion;
  const showProcessTimeline =
    !showStandaloneResponse &&
    (!!thoughtText || !!displayedPlan || chat.tasks.length > 0);
  // plan_thought 的 isFinal 只表示本轮规划思考完成，不等于整个 Agent 已结束。
  // 只有尚未收到思考终态的最新轮次才允许闪动，后续工具执行阶段保持静止。
  const thoughtStreaming =
    Boolean(chat.loading) &&
    thoughtVersionIndex === latestRoundIndex &&
    selectedThoughtRound?.planThoughtFinal !== true;

  const changeActiveChat = useCallback((task: CHAT.Task, targetChat: CHAT.ChatItem) => {
    changeTask?.(task, targetChat);
  }, [changeTask]);

  const handleThoughtPrev = useCallback(() => {
    setThoughtVersionIndex((current) => Math.max(current - 1, 0));
  }, []);

  const handleThoughtNext = useCallback(() => {
    setThoughtVersionIndex((current) => Math.min(current + 1, latestRoundIndex));
  }, [latestRoundIndex]);

  const planSlot = useMemo(() => {
    if (isReactType || !displayedPlan) {
      return null;
    }
    return (
      <PlanSection
        plan={displayedPlan}
        versionLabel={planVersionLabel}
        onPrev={() => setPlanVersionIndex((current) => Math.max(current - 1, 0))}
        onNext={() =>
          setPlanVersionIndex((current) => Math.min(current + 1, latestRoundIndex))
        }
        canPrev={planVersionIndex > 0}
        canNext={planVersionIndex < latestRoundIndex}
        staticSnapshot={planIsHistoricalSnapshot}
      />
    );
  }, [
    isReactType,
    displayedPlan,
    planVersionLabel,
    planVersionIndex,
    latestRoundIndex,
    planIsHistoricalSnapshot,
  ]);

  return (
    <div className="chat-dialogue flex h-full flex-col font-normal">
      {/* 附件 */}
      {(chat.files || []).length ? (
        <div className="mt-5 flex w-full justify-end">
          <AttachmentList
            files={chat.files}
            preview={true}
            review={(file) => changeFile?.(file, chat)}
          />
        </div>
      ) : null}

      {/* 用户消息 */}
      {chat.query ? (
        <div className="user-message-enter mt-5 ml-auto flex w-full max-w-[85%] justify-end">
          <Message from="user" className="max-w-full">
            <MessageContent>
              {chat.query}
            </MessageContent>
          </Message>
        </div>
      ) : null}

      <div className="mt-4 w-full">
        <RunStatus
          status={chat.metrics?.status}
          errorMsg={chat.tip || undefined}
          finishedAt={chat.finishedAt}
        />
      </div>

      {/* AI 回复（Markdown） */}
      {showStandaloneResponse ? (
        <div className="mt-7 flex w-full max-w-[90%] items-end gap-2 md:max-w-[80%]">
          <div
            className="flex h-8 w-8 shrink-0 items-center justify-center self-end rounded-full"
            style={{ boxShadow: "var(--chat-soft-shadow)" }}
            aria-hidden="true"
          >
            <AnimatedOrb size={32} />
          </div>
          <Message from="assistant" className="min-w-0 flex-1">
            <MessageContent>
              <MarkdownRenderer
                markDownContent={chat.response}
                isStreaming={chat.loading}
                className="chat-markdown"
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

      {/* 首包前：完整存在感；已有内容后：紧凑状态条 */}
      {chat.loading && !hasAssistantPayload ? (
        <ThinkingMessage tip={chat.tip} />
      ) : null}
      {chat.loading && hasAssistantPayload ? (
        <RunPresenceBar hint={chat.tip || "正在推进任务…"} compact />
      ) : null}

      {/* Cursor 风格过程时间线 */}
      {showProcessTimeline ? (
        <div className="mt-5 w-full max-w-[min(960px,100%)]">
          <AgentStepTimeline
            chat={chat}
            isPlanSolveMessage={isPlanSolveMessage}
            thoughtText={thoughtText}
            thoughtStreaming={thoughtStreaming}
            thoughtVersionLabel={thoughtVersionLabel}
            thoughtVersionIndex={thoughtVersionIndex}
            thoughtVersionTotal={plannerRounds.length}
            onThoughtPrev={handleThoughtPrev}
            onThoughtNext={handleThoughtNext}
            canThoughtPrev={thoughtVersionIndex > 0}
            canThoughtNext={thoughtVersionIndex < latestRoundIndex}
            planSlot={planSlot}
            changeActiveChat={changeActiveChat}
            changePlan={changePlan}
            changeFile={changeFile}
          />
        </div>
      ) : null}

      {/* GenUI：面向用户的可视化最终产物（过程之后、结论附近） */}
      {featuredGenUi ? (
        <div className="timeline-segment-enter mt-4 w-full max-w-[min(960px,100%)]">
          <div className="rounded-2xl border border-[var(--chat-border)]/60 bg-white p-3 shadow-[var(--chat-soft-shadow)]">
            <GenUiInline
              key={`genui-${chat.requestId}-${featuredGenUi.revision}`}
              tree={featuredGenUi.tree}
              patchCount={featuredGenUi.patchCount}
              sessionId={chat.sessionId}
              messageId={chat.requestId}
              className="w-full max-w-none"
            />
          </div>
        </div>
      ) : null}

      {/* 结论：过程之后的人话结果 */}
      {chat.conclusion ? (
        <div className="timeline-segment-enter mt-3 w-full max-w-[min(960px,100%)]">
          <ConclusionSection
            chat={chat}
            changeFile={changeFile}
            sessionArtifactFiles={sessionArtifactFiles}
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
    prev.sessionArtifactFiles === next.sessionArtifactFiles &&
    prev.changeTask === next.changeTask &&
    prev.changeFile === next.changeFile &&
    prev.changePlan === next.changePlan &&
    prev.onRegenerate === next.onRegenerate
);

export default Dialogue;
