import { FC, useState, useCallback, useMemo, memo, useEffect, useRef } from "react";
import AttachmentList from "@/components/AttachmentList";
import { Message, MessageContent } from "@/components/ai-elements/message";
import { OpenUiMarkdown } from "@/components/openui/OpenUiMarkdown";
import { isPlanSolveConversation } from "@/utils/agentMode";
import {
  buildPlannerRoundsForDisplay,
  syncPlannerVersionCursor,
} from "./plannerHistory";
import { PlanSection } from "./PlanSection";
import { AgentStepTimeline } from "./AgentStepTimeline";
import { MessageToolbar } from "./MessageToolbar";
import { buildConversationTaskData } from "@/utils/chat";
import {
  Collapsible,
  CollapsibleContent,
} from "@/components/ui/collapsible";
import ConclusionSection from "./ConclusionSection";
import ReplayDisclosure, { type ReplayRequestHandler } from "./ReplayDisclosure";
import TurnWorkStatus from "./TurnWorkStatus";
import { UserPromptContexts } from "./UserPromptContexts";
import { parseComposerPromptContexts } from "@/lib/promptContexts";

type Props = {
  chat: CHAT.ChatItem;
  streamingThought?: string;
  deepThink: boolean;
  /** 会话级产物；终答 Markdown 相对引用优先用此表，避免只认本轮 */
  sessionArtifactFiles?: CHAT.TFile[];
  changeTask?: CHAT.OpenTaskHandler;
  changeFile?: CHAT.OpenFileHandler;
  changePlan?: () => void;
  onUndo?: () => void;
  thinkingPanelOpen?: boolean;
  onOpenThinking?: (text: string) => void;
  onSyncThinking?: (text: string) => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem, fileIndex?: number) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  onOpenWorkspaceFiles?: () => void;
  onRequestReplay?: ReplayRequestHandler;
  replayLoading?: boolean;
};

/**
 * 本轮过程区的默认展开态（对齐 ZCode `assistantHistoryDefaultOpen`）。
 *
 * - 异常终态（失败 / 停止 / 超时）：展开 —— 过程是排障的唯一线索；
 * - 运行中 / 等待回答：展开 —— 实时能看到步骤在走；
 * - 已完成但还没产出最终回复：展开 —— 否则这一段会折叠成空；
 * - 正常完成且已有回复：**折叠** —— 只留「已工作 X」一行，点开才看过程。
 */
function resolveProcessDefaultOpen(chat: CHAT.ChatItem): boolean {
  const status = String(chat.metrics?.status || "").trim().toUpperCase();
  if (status === "FAILED" || status === "STOPPED" || status === "TIMEOUT") {
    return true;
  }
  if (chat.loading || status === "RUNNING" || status === "WAITING_INPUT") {
    return true;
  }
  return !chat.response && !chat.conclusion;
}

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
    onOpenWorkspaceFiles,
    onRequestReplay,
    replayLoading = false,
  } = props;
  const isPlanSolveMessage = isPlanSolveConversation(chat.agentType, deepThink);
  const isReactType = !isPlanSolveMessage;
  // 用户消息正文里可能拼着上下文尾块（# Code comments: / # Web page elements: / # userselect:）。
  // 正文只渲染剥离后的部分，尾块在这里还原成卡片，避免原始 markdown 糊在气泡里。
  const userPromptContexts = useMemo(
    () => parseComposerPromptContexts(chat.query || ""),
    [chat.query]
  );
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
  /**
   * 本轮执行过程（`AgentStepTimeline`）的展开态。
   * 由「已工作 X 分 X 秒」状态条控制，对齐 ZCode `AssistantHistoryStatus`
   * 的折叠语义：展开后还能再点折叠，状态条自身常驻不消失。
   *
   * 初始值对齐 ZCode `conversationTurnRenderUnits.ts` 的 `assistantHistoryDefaultOpen`：
   * 异常态 / 运行中 / 还没产出最终回复 → 展开；正常完成且已有回复 → 折叠。
   */
  const [processOpen, setProcessOpen] = useState(() =>
    resolveProcessDefaultOpen(chat)
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
  const showStandaloneResponse =
    isReactType && !!chat.response && !chat.conclusion;
  const showProcessTimeline =
    !showStandaloneResponse &&
    (!!thoughtText || !!displayedPlan || timelineChat.tasks.length > 0);
  // plan_thought 的 isFinal 只表示本轮规划思考完成，不等于整个 Agent 已结束。
  // 计划已出或思考已终态后不再保持流式展开，避免整轮 loading 期间无法折叠。
  const thoughtStreaming =
    Boolean(chat.loading) &&
    Boolean(thoughtText.trim()) &&
    thoughtVersionIndex === latestRoundIndex &&
    selectedThoughtRound?.planThoughtFinal !== true &&
    !selectedThoughtRound?.plan;

  const changeActiveChat = useCallback(
    (
      task: CHAT.Task,
      targetChat: CHAT.ChatItem,
      backTarget?: CHAT.AgentDetailTarget
    ) => {
      changeTask?.(task, targetChat, backTarget);
    },
    [changeTask]
  );

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
    <div
      className="chat-dialogue flex h-full flex-col font-normal"
      data-request-id={chat.requestId}
    >
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
        <div
          className="user-message-enter mt-5 ml-auto flex w-full max-w-[78%] flex-col items-end gap-1"
          data-user-message-anchor
        >
          <Message from="user" className="max-w-full">
            <MessageContent>
              {userPromptContexts.visibleContent}
            </MessageContent>
          </Message>
          <UserPromptContexts contexts={userPromptContexts} />
          {onUndo && !chat.loading ? (
            <MessageToolbar onUndo={onUndo} />
          ) : null}
        </div>
      ) : null}

      {/* 运行中：每秒跳动的「已工作 X」；结束后同位置换成 ReplayDisclosure */}
      {chat.loading && chat.startedAt ? (
        <div className="mt-2 w-full">
          <TurnWorkStatus startedAt={chat.startedAt} />
        </div>
      ) : null}

      {onRequestReplay &&
        chat.requestId &&
        chat.replayAvailable !== false &&
        !chat.loading ? (
          <div className="mt-2 w-full">
            <ReplayDisclosure
              chat={chat}
              onRequestReplay={onRequestReplay}
              replayLoading={replayLoading}
              open={processOpen}
              // 只有真的存在过程区时才给折叠能力，否则按钮退化为纯回放入口
              onToggle={showProcessTimeline ? setProcessOpen : undefined}
            />
          </div>
        ) : null}

      {/* AI 回复（Markdown） */}
      {showStandaloneResponse ? (
        <div className="mt-7 w-full max-w-[94%]">
          <Message from="assistant" className="min-w-0 w-full">
            <MessageContent>
              <OpenUiMarkdown
                content={chat.response || ""}
                streaming={chat.loading}
                artifactFiles={sessionArtifactFiles}
                className="chat-markdown kimi-md"
                sessionId={chat.sessionId}
                messageId={chat.requestId}
              />
            </MessageContent>
            {!chat.loading ? (
              <MessageToolbar response={chat.response} />
            ) : null}
          </Message>
        </div>
      ) : null}

      {/* 运行状态已上移到 ChatView 顶栏，对话区不再重复展示 tip */}

      {/* Cursor 风格过程时间线；展开态由「已工作 X」状态条控制 */}
      {showProcessTimeline ? (
        <Collapsible open={processOpen} onOpenChange={setProcessOpen}>
          <CollapsibleContent className="chat-process-collapse">
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
          </CollapsibleContent>
        </Collapsible>
      ) : null}

      {/* 结论：过程之后的人话结果 */}
      {chat.conclusion ? (
        <div className="timeline-segment-enter mt-3 w-full max-w-[min(960px,100%)]">
          <ConclusionSection
            chat={chat}
            changeFile={changeFile}
            sessionArtifactFiles={sessionArtifactFiles}
            onOpenWorkspaceFiles={onOpenWorkspaceFiles}
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
    prev.onOpenAgent === next.onOpenAgent &&
    prev.onOpenWorkspaceFiles === next.onOpenWorkspaceFiles &&
    prev.onRequestReplay === next.onRequestReplay &&
    prev.replayLoading === next.replayLoading
);

export default Dialogue;
