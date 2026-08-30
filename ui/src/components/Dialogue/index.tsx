import { FC, useState, useCallback, useMemo, memo, useEffect, useRef, useSyncExternalStore } from "react";
import AttachmentList from "@/components/AttachmentList";
import { getTaskFiles } from "@/utils/taskArtifacts";
import {
  Message,
  MessageContent,
} from "@/components/ai-elements/message";
import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
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
import { buildConversationTaskData } from "@/utils/chat";
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
  onUndo?: () => void;
  thinkingPanelOpen?: boolean;
  onOpenThinking?: (text: string) => void;
  onSyncThinking?: (text: string) => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

const ConclusionSection: FC<{
  chat: CHAT.ChatItem;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
  sessionArtifactFiles?: CHAT.TFile[];
}> = ({ chat, changeFile, sessionArtifactFiles }) => {
  const summaryText = resolveTaskSummaryText(chat.conclusion);
  const summary = summaryText || "任务已完成";
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
      {/* 对齐 kimi-web a-msg：终答正文 + 底部复制脚 */}
      <Message from="assistant" className="min-w-0 w-full">
        <MessageContent>
          <MarkdownRenderer
            markDownContent={summary}
            isStreaming={summaryStreaming}
            artifactFiles={artifactFiles}
            className="chat-markdown conclusion-markdown kimi-md"
          />
        </MessageContent>
        {!summaryStreaming && summaryText ? (
          <MessageToolbar response={summaryText} alwaysVisible />
        ) : null}
      </Message>
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
    onUndo,
    thinkingPanelOpen = false,
    onOpenThinking,
    onSyncThinking,
    onOpenToolDiff,
    onOpenAgent,
  } = props;
  const isPlanSolveMessage = isPlanSolveConversation(chat.agentType, deepThink);
  const isReactType = !isPlanSolveMessage;
  const timelineChat = useMemo(() => {
    const hasTimelineChildren = (chat.tasks || []).some((group) =>
      (group || []).some((container) => (container.children || []).length > 0)
    );
    if (hasTimelineChildren || !(chat.multiAgent?.tasks || []).length) {
      return chat;
    }
    // 工作区使用 taskList，主对话使用 chat.tasks；流式快照短暂不同步时，
    // 从事实层重建一次时间线，避免主 Agent 的思考和执行步骤消失。
    return buildConversationTaskData(chat, deepThink).currentChat;
  }, [chat, deepThink]);
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
  const showStandaloneResponse =
    isReactType && !!chat.response && !chat.conclusion;
  const showProcessTimeline =
    !showStandaloneResponse &&
    (!!thoughtText || !!displayedPlan || timelineChat.tasks.length > 0);
  // plan_thought 的 isFinal 只表示本轮规划思考完成，不等于整个 Agent 已结束。
  // 只有尚未收到思考终态的最新轮次才允许闪动，后续工具执行阶段保持静止。
  const thoughtStreaming =
    Boolean(chat.loading) &&
    thoughtVersionIndex === latestRoundIndex &&
    selectedThoughtRound?.planThoughtFinal !== true;

  const changeActiveChat = useCallback((task: CHAT.Task, targetChat: CHAT.ChatItem) => {
    changeTask?.(task, targetChat);
  }, [changeTask]);

  useEffect(() => {
    if (thinkingPanelOpen && thoughtText) {
      onSyncThinking?.(thoughtText);
    }
  }, [thinkingPanelOpen, thoughtText, onSyncThinking]);

  const handleOpenThinking = useCallback(
    (text: string) => {
      onOpenThinking?.(text);
    },
    [onOpenThinking]
  );

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
        <div className="user-message-enter mt-5 ml-auto flex w-full max-w-[78%] flex-col items-end gap-1">
          <Message from="user" className="max-w-full">
            <MessageContent>
              {chat.query}
            </MessageContent>
          </Message>
          {onUndo && !chat.loading ? (
            <MessageToolbar onUndo={onUndo} />
          ) : null}
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
        <div className="mt-7 w-full max-w-[94%]">
          <Message from="assistant" className="min-w-0 w-full">
            <MessageContent>
              <MarkdownRenderer
                markDownContent={chat.response}
                isStreaming={chat.loading}
                className="chat-markdown kimi-md"
              />
            </MessageContent>
            {!chat.loading ? (
              <MessageToolbar response={chat.response} />
            ) : null}
          </Message>
        </div>
      ) : null}

      {/* 运行状态已上移到 ChatView 顶栏，对话区不再重复展示 tip */}

      {/* Cursor 风格过程时间线 */}
      {showProcessTimeline ? (
        <div className="mt-5 w-full max-w-[min(960px,100%)]">
          <AgentStepTimeline
            chat={timelineChat}
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
            onOpenThinking={handleOpenThinking}
            onOpenToolDiff={onOpenToolDiff}
            onOpenAgent={onOpenAgent}
          />
        </div>
      ) : null}

      {/* GenUI：面向用户的可视化最终产物（过程之后、结论附近） */}
      {featuredGenUi ? (
        <div className="timeline-segment-enter mt-4 w-full max-w-[min(1080px,100%)]">
          <GenUiInline
            key={`genui-${chat.requestId}-${featuredGenUi.revision}`}
            tree={featuredGenUi.tree}
            patchCount={featuredGenUi.patchCount}
            sessionId={chat.sessionId}
            messageId={chat.requestId}
            className="w-full max-w-none"
          />
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
    prev.onUndo === next.onUndo &&
    prev.thinkingPanelOpen === next.thinkingPanelOpen &&
    prev.onOpenThinking === next.onOpenThinking &&
    prev.onSyncThinking === next.onSyncThinking &&
    prev.onOpenToolDiff === next.onOpenToolDiff &&
    prev.onOpenAgent === next.onOpenAgent
);

export default Dialogue;
