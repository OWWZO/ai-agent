import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { X } from "lucide-react";
import { resolveSubAgentDisplay } from "@/utils/chat/subagent";
import { chatItemFromSubAgent } from "@/utils/chat/subAgentChat";
import { buildChildSessionChat } from "@/utils/chat/subAgentChildSession";
import { projectAgentMember } from "@/utils/chat/agentRuntimeProjector";
import { useSubAgentSession } from "./useSubAgentSession";
import {
  formatAgentElapsed,
  resolveAgentPhaseLabel,
  resolveAgentPhaseTone,
} from "./agentProgressGroups";
import { cn } from "@/lib/utils";
import { StatusDot } from "./tools/StatusDot";
import Dialogue from "./index";
import { getTaskFiles } from "@/utils/taskArtifacts";

/**
 * 子会话真实详情面板开关（前端侧，与后端 autobots.agent.subagent-isolated-session 成对）。
 * <p>
 * 关闭时沿用旧合成投影兜底；开启时读真实子会话 history + 子会话流。
 * 环境变量未提供时默认关闭，避免部署漏配把右侧面板打成空白。
 */
export const SUBAGENT_CHILD_SESSION_ENABLED =
  (import.meta.env?.VITE_SUBAGENT_CHILD_SESSION as string | undefined) === "1";

type AgentDetailPanelProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  onClose?: () => void;
  changeActiveChat?: CHAT.OpenTaskHandler;
  changeFile?: CHAT.OpenFileHandler;
  sessionArtifactFiles?: CHAT.TFile[];
  changePlan?: () => void;
  onOpenThinking?: (text: string) => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem, fileIndex?: number) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  onOpenWorkspaceFiles?: () => void;
};

export const AgentDetailPanel = memo(function AgentDetailPanel({
  tool,
  chat,
  onClose,
  changeActiveChat,
  changePlan,
  onOpenToolDiff,
  onOpenAgent,
  changeFile,
  sessionArtifactFiles,
  onOpenThinking,
  onOpenWorkspaceFiles,
}: AgentDetailPanelProps) {
  const bodyRef = useRef<HTMLDivElement | null>(null);
  const sub = useMemo(() => resolveSubAgentDisplay(tool), [tool]);
  const member = useMemo(() => projectAgentMember(tool), [tool]);
  const nested = tool.children || [];
  const syntheticChat = useMemo(
    () => chatItemFromSubAgent(tool, chat),
    [tool, chat]
  );
  const duration = formatAgentElapsed(sub);
  const phaseLabel = resolveAgentPhaseLabel(member?.phase, sub.status);
  const phaseTone = resolveAgentPhaseTone(phaseLabel);

  // 子会话真实详情：只有开关开启且父卡确实带 childSessionId 时才订阅。
  // 不具备 childSessionId（旧会话 / 未落库成功）时自动回退合成投影。
  const childSessionId = SUBAGENT_CHILD_SESSION_ENABLED ? sub.childSessionId : "";
  const childSession = useSubAgentSession(childSessionId, Boolean(childSessionId));
  const useRealChildSession = Boolean(childSessionId);

  // 数据源：真实子会话继续由会话级渲染链路消费；本面板在真实模式下
  // 以「历史已就绪 + 实时帧已到达」作为渲染准入，避免打开瞬间空白。
  const [realSessionReady, setRealSessionReady] = useState(false);
  useEffect(() => {
    if (!useRealChildSession) {
      setRealSessionReady(false);
      return;
    }
    if (childSession.ready) {
      setRealSessionReady(true);
    }
  }, [useRealChildSession, childSession.ready]);

  // 真实子会话：history / replay / 实时帧 → 走与主会话同构的渲染链路（不新写 Pane）。
  const childSessionChat = useMemo(
    () =>
      useRealChildSession
        ? buildChildSessionChat({
            childSessionId,
            history: childSession.history,
            replay: childSession.replay,
            frames: childSession.frames,
            fallbackQuery: sub.prompt || sub.description,
          })
        : null,
    [useRealChildSession, childSessionId, childSession.history, childSession.replay, childSession.frames, sub.prompt, sub.description]
  );

  const sourceChat = useMemo(
    () => (useRealChildSession && childSessionChat ? childSessionChat : syntheticChat),
    [useRealChildSession, childSessionChat, syntheticChat]
  );

  const hasTimeline = sourceChat.tasks.length > 0;
  const hasConclusion = Boolean(sourceChat.conclusion);
  const hasConclusionFiles = getTaskFiles(sourceChat.conclusion).length > 0;
  const hasBody =
    Boolean(sourceChat.query) || hasTimeline || hasConclusion || hasConclusionFiles;

  const handleChangeActiveChat = useCallback(
    (task: CHAT.Task) => {
      changeActiveChat?.(task, chat);
    },
    [changeActiveChat, chat]
  );
  const handleOpenToolDiff = useCallback(
    (task: CHAT.Task, _chat?: CHAT.ChatItem, fileIndex?: number) => {
      onOpenToolDiff?.(task, chat, fileIndex);
    },
    [onOpenToolDiff, chat]
  );
  const handleOpenAgent = useCallback(
    (task: CHAT.Task) => {
      onOpenAgent?.(task, chat);
    },
    [onOpenAgent, chat]
  );
  const handleChangeFile = useCallback(
    (file: CHAT.TFile) => {
      changeFile?.(file, chat);
    },
    [changeFile, chat]
  );

  useEffect(() => {
    const el = bodyRef.current;
    if (!el) return;
    const frame = requestAnimationFrame(() => {
      const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 32;
      if (atBottom) {
        el.scrollTop = el.scrollHeight;
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [
    nested.length,
    sub.content,
    sub.status,
    sub.liveText,
    syntheticChat.loading,
    childSession.frames.length,
    childSession.replay,
    childSession.history,
  ]);

  return (
    <div className="kimi-detail-panel">
      <div className="kimi-detail-panel-head">
        <div className="min-w-0 flex-1">
          <div className="text-ui-sm text-[var(--color-text-faint)]">子智能体</div>
          <div className="truncate text-ui-base font-medium text-[var(--color-text)]">
            {sub.description || sub.subagentType || "Agent"}
          </div>
          <div className="mt-1 flex min-w-0 flex-wrap items-center gap-2 text-ui-sm text-[var(--color-text-muted)]">
            <span
              className={cn(
                "kimi-agent-phase-badge",
                phaseTone === "running" && "is-running",
                phaseTone === "ok" && "is-ok",
                phaseTone === "error" && "is-error"
              )}
            >
              {phaseTone === "running" ? <StatusDot status="running" /> : null}
              {phaseLabel}
            </span>
            {sub.subagentType ? <span>{sub.subagentType}</span> : null}
            {nested.length > 0 ? <span>{nested.length} tools</span> : null}
            {duration ? <span>{duration}</span> : null}
            {sub.runInBackground ? (
              <span className="rounded border border-[var(--color-line)] px-1.5 py-0.5 text-ui-xs uppercase tracking-wide">
                background
              </span>
            ) : null}
            {useRealChildSession ? (
              <span
                className="rounded border border-[var(--color-line)] px-1.5 py-0.5 text-ui-xs uppercase tracking-wide"
                title={`子会话 ${childSessionId}`}
              >
                session
              </span>
            ) : null}
          </div>
        </div>
        <button
          type="button"
          className="flex h-7 w-7 items-center justify-center rounded-full text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-sunken)] hover:text-[var(--color-text)]"
          aria-label="关闭"
          onClick={onClose}
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <div ref={bodyRef} className="kimi-detail-panel-body px-3 py-2">
        {sub.errorMsg ? (
          <div className="mb-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-ui-caption text-red-700">
            {sub.errorMsg}
          </div>
        ) : null}

        {childSession.error ? (
          <div className="mb-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-ui-caption text-amber-700">
            子会话读取失败，已回退本地投影：{childSession.error}
          </div>
        ) : null}

        {/* 真实子会话模式下 history 拉回前显示骨架，不塞假轨迹（计划书 §5.4） */}
        {useRealChildSession && !realSessionReady && !childSession.error ? (
          <div className="kimi-detail-panel-empty">加载子会话…</div>
        ) : null}

        {hasBody ? (
          <Dialogue
            key={
              useRealChildSession
                ? `child-session-${childSessionId}`
                : tool.id || tool.messageId || "subagent-dialogue"
            }
            chat={sourceChat}
            deepThink={false}
            sessionArtifactFiles={sessionArtifactFiles}
            changeTask={handleChangeActiveChat}
            changeFile={handleChangeFile}
            changePlan={changePlan}
            onOpenThinking={onOpenThinking}
            onOpenToolDiff={handleOpenToolDiff}
            onOpenAgent={handleOpenAgent}
            onOpenWorkspaceFiles={onOpenWorkspaceFiles}
          />
        ) : null}

        {!hasBody && (!useRealChildSession || realSessionReady || childSession.error) ? (
          <div className="kimi-detail-panel-empty">
            暂无详情
          </div>
        ) : null}
      </div>
    </div>
  );
});
