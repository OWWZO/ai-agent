import { memo, useEffect, useMemo, useRef, useState } from "react";
import { X } from "lucide-react";
import { resolveSubAgentDisplay } from "@/utils/chat/subagent";
import { projectAgentMember } from "@/utils/chat/agentRuntimeProjector";
import { ToolCallView, resolveStackPosition } from "./tools";
import {
  buildAgentProgressGroups,
  foldCount,
  formatAgentElapsed,
  resolveAgentPhaseLabel,
  resolveAgentPhaseTone,
  shouldFoldGroup,
  OUTPUT_HEAD,
  OUTPUT_TAIL,
  type AgentProgressGroup,
} from "./agentProgressGroups";
import { cn } from "@/lib/utils";
import { StatusDot } from "./tools/StatusDot";

type AgentDetailPanelProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  onClose?: () => void;
  changeActiveChat?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  onOpenToolDiff?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

function ProgressGroupView({ group }: { group: AgentProgressGroup }) {
  const [expanded, setExpanded] = useState(false);
  const folded = shouldFoldGroup(group) && !expanded;

  return (
    <div className="kimi-agent-progress-group">
      {group.call ? (
        <div className="kimi-agent-progress-call">
          <span className="kimi-agent-progress-glyph" aria-hidden>
            ▶
          </span>
          {group.call}
        </div>
      ) : null}
      {group.output.length > 0 ? (
        <div className="kimi-agent-progress-output">
          {folded ? (
            <>
              {group.output.slice(0, OUTPUT_HEAD).map((line, li) => (
                <div key={`h-${li}`} className="kimi-agent-progress-line">
                  {line}
                </div>
              ))}
              <button
                type="button"
                className="kimi-agent-progress-fold"
                onClick={() => setExpanded(true)}
              >
                … ({foldCount(group)} more)
              </button>
              {group.output.slice(-OUTPUT_TAIL).map((line, li) => (
                <div key={`t-${li}`} className="kimi-agent-progress-line">
                  {line}
                </div>
              ))}
            </>
          ) : (
            group.output.map((line, li) => (
              <div key={li} className="kimi-agent-progress-line">
                {line}
              </div>
            ))
          )}
        </div>
      ) : null}
    </div>
  );
}

export const AgentDetailPanel = memo(function AgentDetailPanel({
  tool,
  chat,
  onClose,
  changeActiveChat,
  changePlan,
  onOpenToolDiff,
  onOpenAgent,
}: AgentDetailPanelProps) {
  const bodyRef = useRef<HTMLDivElement | null>(null);
  const sub = useMemo(() => resolveSubAgentDisplay(tool), [tool]);
  const member = useMemo(() => projectAgentMember(tool), [tool]);
  const nested = tool.children || [];
  const progressGroups = useMemo(() => buildAgentProgressGroups(tool), [tool]);
  const liveText = (sub.liveText || "").trimEnd();
  const duration = formatAgentElapsed(sub);
  const phaseLabel = resolveAgentPhaseLabel(member?.phase, sub.status);
  const phaseTone = resolveAgentPhaseTone(phaseLabel);

  useEffect(() => {
    const el = bodyRef.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 32;
    if (!atBottom) return;
    el.scrollTop = el.scrollHeight;
  }, [
    nested.length,
    sub.content,
    sub.status,
    liveText,
    progressGroups.length,
    progressGroups.map((g) => g.output.length).join(","),
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

        {sub.subagentType ? (
          <div className="mb-2 font-mono text-[12px] text-[var(--color-text-muted)]">
            {sub.subagentType}
          </div>
        ) : null}

        {sub.prompt ? (
          <div className="kimi-agent-field mb-3">
            <div className="kimi-agent-field-label">Task</div>
            <pre className="kimi-agent-field-body">{sub.prompt}</pre>
          </div>
        ) : null}

        {liveText ? (
          <div className="kimi-agent-field mb-3">
            <div className="kimi-agent-field-label">Output</div>
            <pre className="kimi-agent-field-body kimi-agent-live">{liveText}</pre>
          </div>
        ) : null}

        {progressGroups.length > 0 ? (
          <div className="kimi-agent-field mb-3">
            <div className="kimi-agent-field-label">Progress</div>
            <div className="kimi-agent-field-body">
              {progressGroups.map((group) => (
                <ProgressGroupView key={group.key} group={group} />
              ))}
            </div>
          </div>
        ) : null}

        {nested.length > 0 ? (
          <div className="kimi-agent-field mb-3">
            <div className="kimi-agent-field-label">Tools</div>
            <div className="flex flex-col gap-0.5">
              {nested.map((child, index) => (
                <ToolCallView
                  key={child.id || child.messageId || child.taskId || index}
                  tool={child}
                  chat={chat}
                  stackPosition={resolveStackPosition(index, nested.length)}
                  defaultExpanded={false}
                  changeActiveChat={changeActiveChat || (() => undefined)}
                  changePlan={changePlan}
                  onOpenToolDiff={onOpenToolDiff}
                  onOpenAgent={onOpenAgent}
                />
              ))}
            </div>
          </div>
        ) : null}

        {sub.content ? (
          <div className="kimi-agent-field mb-3">
            <div className="kimi-agent-field-label">Result</div>
            <pre className="kimi-agent-field-body">{sub.content}</pre>
          </div>
        ) : null}

        {!sub.content &&
        nested.length === 0 &&
        !liveText &&
        progressGroups.length === 0 ? (
          <div className="kimi-detail-panel-empty">
            {sub.status === "running" ? "子智能体执行中…" : "暂无详情"}
          </div>
        ) : null}
      </div>
    </div>
  );
});
