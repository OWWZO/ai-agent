import { memo, useCallback, useEffect, useMemo, useRef } from "react";
import { X } from "lucide-react";
import { resolveSubAgentDisplay } from "@/utils/chat/subagent";
import { chatItemFromSubAgent } from "@/utils/chat/subAgentChat";
import { projectAgentMember } from "@/utils/chat/agentRuntimeProjector";
import {
  formatAgentElapsed,
  resolveAgentPhaseLabel,
  resolveAgentPhaseTone,
} from "./agentProgressGroups";
import { cn } from "@/lib/utils";
import { StatusDot } from "./tools/StatusDot";
import Dialogue from "./index";
import { getTaskFiles } from "@/utils/taskArtifacts";

type AgentDetailPanelProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  onClose?: () => void;
  changeActiveChat?: CHAT.OpenTaskHandler;
  changeFile?: CHAT.OpenFileHandler;
  sessionArtifactFiles?: CHAT.TFile[];
  changePlan?: () => void;
  onOpenThinking?: (text: string) => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
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
  const hasTimeline = syntheticChat.tasks.length > 0;
  const hasConclusion = Boolean(syntheticChat.conclusion);
  const hasConclusionFiles = getTaskFiles(syntheticChat.conclusion).length > 0;
  const hasBody =
    Boolean(syntheticChat.query) || hasTimeline || hasConclusion || hasConclusionFiles;

  const handleChangeActiveChat = useCallback(
    (task: CHAT.Task) => {
      changeActiveChat?.(task, chat);
    },
    [changeActiveChat, chat]
  );
  const handleOpenToolDiff = useCallback(
    (task: CHAT.Task) => {
      onOpenToolDiff?.(task, chat);
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
  ]);

  return (
    <div className="kimi-detail-panel">
      <div className="kimi-detail-panel-head">
        <div className="min-w-0 flex-1">
          <div className="text-[12px] text-[var(--color-text-faint)]">子智能体</div>
          <div className="truncate text-[14px] font-medium text-[var(--color-text)]">
            {sub.description || sub.subagentType || "Agent"}
          </div>
          <div className="mt-1 flex min-w-0 flex-wrap items-center gap-2 text-[12px] text-[var(--color-text-muted)]">
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
              <span className="rounded border border-[var(--color-line)] px-1.5 py-0.5 text-[10px] uppercase tracking-wide">
                background
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
          <div className="mb-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-[13px] text-red-700">
            {sub.errorMsg}
          </div>
        ) : null}

        {hasBody ? (
          <Dialogue
            key={tool.id || tool.messageId || "subagent-dialogue"}
            chat={syntheticChat}
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

        {!hasBody ? (
          <div className="kimi-detail-panel-empty">
            暂无详情
          </div>
        ) : null}
      </div>
    </div>
  );
});
