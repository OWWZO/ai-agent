import { FC, useState, useCallback, useMemo, memo } from "react";
import { motion } from "motion/react";
import AttachmentList from "@/components/AttachmentList";
import LoadingSpinner from "@/components/LoadingSpinner";
import { buildAction, getIcon, buildAttachment } from "@/utils/chat";
import {
  Message,
  MessageContent,
  MessageResponse,
  MessageActions,
  MessageAction,
} from "@/components/ai-elements/message";
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

type Props = {
  chat: CHAT.ChatItem;
  streamingThought?: string;
  deepThink: boolean;
  changeTask?: (task: CHAT.Task) => void;
  changeFile?: (file: CHAT.TFile) => void;
  changePlan?: () => void;
  onRegenerate?: () => void;
};

const PlanSection: FC<{ plan: CHAT.PlanItem[] }> = memo(({ plan }) => (
  <motion.div
    initial={{ opacity: 0, y: 10 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ duration: 0.24, ease: [0.25, 0.46, 0.45, 0.94] }}
    className="overflow-hidden rounded-2xl bg-[var(--chat-surface-soft)]/90 px-4 py-4 shadow-[var(--shadow-sm)] ring-0"
  >
    <div className="mb-4 flex items-center gap-3">
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
          任务计划
        </p>
      </div>
    </div>
    <div className="space-y-2.5">
      {plan.map((p, i) => (
        <motion.div
          key={`${p.name}-${i}`}
          initial={{ opacity: 0, x: -6 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{
            delay: Math.min(i * 0.06, 0.36),
            duration: 0.22,
            ease: [0.25, 0.46, 0.45, 0.94],
          }}
          className="rounded-xl bg-[var(--chat-surface)]/75 px-3 py-3 shadow-[var(--shadow-xs)]"
        >
          <div className="flex items-start gap-3">
            <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[var(--chat-surface-muted)] text-[12px] font-semibold tabular-nums text-[var(--chat-text-soft)]">
              {i + 1}
            </span>
            <div className="min-w-0 flex-1 space-y-2">
              <div className="text-[14px] font-medium leading-snug tracking-[-0.01em] text-[var(--chat-text)]">
                {p.name}
              </div>
              <ul className="space-y-1.5">
                {p.list.map((step, j) => (
                  <li
                    key={j}
                    className="flex gap-2 text-[13px] leading-relaxed text-[var(--chat-text-soft)]"
                  >
                    <span className="w-5 shrink-0 pt-px font-mono text-[11px] tabular-nums text-[var(--chat-text-muted)]">
                      {j + 1}.
                    </span>
                    <span>{step}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </motion.div>
      ))}
    </div>
  </motion.div>
));

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
        <div className="mt-[8px]">
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
          {tool.resultMap?.steps
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
      return (
        <div className="mt-[8px]">
          <div className="mb-[8px]">{tool.resultMap.taskSummary}</div>
          <AttachmentList
            files={buildAttachment(tool.resultMap.fileList!)}
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
        {!isReactType ? <div className="font-[500]">{t.task}</div> : null}
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

type TimelineGroupType = "thought" | "plan" | "task_group" | "conclusion" | "tool";

type TimelineGroup = {
  type: TimelineGroupType;
  seq: number;
  entries: CHAT.TimelineEntry[];
  taskId?: string;
};

function normalizeTimelineEntries(timeline: CHAT.TimelineEntry[]): CHAT.TimelineEntry[] {
  if (!timeline.length) {
    return timeline;
  }

  const normalized: CHAT.TimelineEntry[] = [];

  for (let index = 0; index < timeline.length; index += 1) {
    const current = timeline[index];
    const next = timeline[index + 1];

    const isDuplicatedDeepSearchStage =
      current.type === "deep_search" &&
      current.subType === "extend" &&
      next?.type === "deep_search" &&
      next.subType === "search" &&
      current.messageIdExt &&
      current.messageIdExt === next.messageIdExt &&
      current.taskId === next.taskId &&
      (current.content || "") === (next.content || "");

    // 历史回放里同一轮搜索会连续写入 extend/search 两个阶段。
    // 当两条记录指向同一 messageId 且内容一致时，只保留最终 search，避免界面重复展示。
    if (isDuplicatedDeepSearchStage) {
      continue;
    }

    normalized.push(current);
  }

  return normalized;
}

function groupTimelineEntries(timeline: CHAT.TimelineEntry[]): TimelineGroup[] {
  const groups: TimelineGroup[] = [];

  [...normalizeTimelineEntries(timeline)]
    .sort((left, right) => left.seq - right.seq)
    .forEach((entry) => {
    if (entry.type === "plan_thought") {
      const last = groups[groups.length - 1];
      if (last?.type === "thought" && !last.entries[last.entries.length - 1]?.isFinal) {
        last.entries.push(entry);
      } else {
        groups.push({ type: "thought", seq: entry.seq, entries: [entry] });
      }
      return;
    }

    if (entry.type === "plan") {
      groups.push({ type: "plan", seq: entry.seq, entries: [entry] });
      return;
    }

    if (entry.type === "agent_stream" || entry.type === "result") {
      const last = groups[groups.length - 1];
      if (last?.type === "conclusion") {
        last.entries.push(entry);
      } else {
        groups.push({ type: "conclusion", seq: entry.seq, entries: [entry] });
      }
      return;
    }

    if (entry.type === "task") {
      groups.push({ type: "task_group", seq: entry.seq, entries: [entry], taskId: entry.taskId });
      return;
    }

    const last = groups[groups.length - 1];
    const canAttachToLastTaskGroup =
      !!entry.taskId &&
      last?.type === "task_group" &&
      last.taskId === entry.taskId;

    if (canAttachToLastTaskGroup) {
      last.entries.push(entry);
      return;
    }

      groups.push({ type: "tool", seq: entry.seq, entries: [entry], taskId: entry.taskId });
    });

  return groups;
}

function findTaskByEntry(chat: CHAT.ChatItem, entry: CHAT.TimelineEntry): CHAT.Task | undefined {
  const matchTask = (task: CHAT.Task) => {
    if (task.messageType !== entry.type) {
      return false;
    }

    if (entry.messageIdExt) {
      return task.messageId === entry.messageIdExt;
    }

    if (entry.taskId && task.taskId !== entry.taskId) {
      return false;
    }

    if (entry.subType && task.resultMap?.messageType && task.resultMap.messageType !== entry.subType) {
      return false;
    }

    return Boolean(entry.taskId && task.taskId === entry.taskId);
  };

  for (const group of chat.tasks || []) {
    for (const task of group) {
      if (matchTask(task)) {
        return task;
      }
      for (const child of task.children || []) {
        if (matchTask(child)) {
          return child;
        }
      }
    }
  }

  return undefined;
}

const SimpleToolCard: FC<{
  entry: CHAT.TimelineEntry;
  onClick?: () => void;
}> = ({ entry, onClick }) => {
  const clickable = typeof onClick === "function";
  const { toneClass, surfaceClass, statusText } = (() => {
    switch (entry.type) {
      case "deep_search":
        return {
          toneClass: "text-[#0071e3]",
          surfaceClass: "bg-[rgba(0,113,227,0.08)] ring-[rgba(0,113,227,0.12)]",
          statusText: entry.isFinal ? "已完成" : "搜索中",
        };
      case "file":
        return {
          toneClass: "text-[#0f766e]",
          surfaceClass: "bg-[rgba(15,118,110,0.08)] ring-[rgba(15,118,110,0.12)]",
          statusText: entry.isFinal ? "已生成" : "处理中",
        };
      case "browser":
        return {
          toneClass: "text-[#7c3aed]",
          surfaceClass: "bg-[rgba(124,58,237,0.08)] ring-[rgba(124,58,237,0.12)]",
          statusText: entry.isFinal ? "已完成" : "浏览中",
        };
      case "code":
      case "html":
      case "markdown":
        return {
          toneClass: "text-[#111827]",
          surfaceClass: "bg-[rgba(17,24,39,0.06)] ring-[rgba(17,24,39,0.10)]",
          statusText: entry.isFinal ? "已生成" : "生成中",
        };
      default:
        return {
          toneClass: "text-[#0071e3]",
          surfaceClass: "bg-[rgba(0,113,227,0.08)] ring-[rgba(0,113,227,0.12)]",
          statusText: entry.isFinal ? "已完成" : "处理中",
        };
    }
  })();

  const content = entry.content?.trim() || "历史回放仅保存了这一步的摘要信息。";

  return (
    <div
      className={`mt-2 flex items-start gap-3 rounded-2xl border border-[rgba(17,24,39,0.06)] px-3 py-3 shadow-[0_8px_24px_rgba(15,23,42,0.04)] transition-all duration-200 ${clickable ? "cursor-pointer hover:-translate-y-[1px] hover:bg-muted/20" : "bg-white/70"}`}
      onClick={onClick}
    >
      <div className={`flex size-9 shrink-0 items-center justify-center rounded-xl ring-1 ${toneClass} ${surfaceClass}`}>
        <i className={`font_family ${getIcon(entry.type)} text-[17px] leading-none [text-shadow:none]`}></i>
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 items-center gap-2">
          <span className="truncate text-[14px] font-medium text-foreground">{entry.title}</span>
          <span className="shrink-0 rounded-full bg-[var(--chat-surface-soft)] px-2 py-0.5 text-[11px] font-medium text-[var(--chat-text-muted)]">
            {statusText}
          </span>
        </div>
        <div className="mt-1 text-[13px] leading-5 text-muted-foreground">
          {content}
        </div>
      </div>
    </div>
  );
};

const TimelineReplay: FC<{
  timeline: CHAT.TimelineEntry[];
  chat: CHAT.ChatItem;
  changeTask?: (task: CHAT.Task) => void;
  changeFile?: (file: CHAT.TFile) => void;
  changePlan?: () => void;
}> = ({ timeline, chat, changeTask, changeFile, changePlan }) => {
  const groups = useMemo(() => groupTimelineEntries(timeline), [timeline]);
  const lastConclusionIndex = useMemo(
    () => groups.reduce((result, group, index) => (group.type === "conclusion" ? index : result), -1),
    [groups]
  );

  const renderToolEntry = useCallback((entry: CHAT.TimelineEntry, index: number) => {
    const matchedTask = findTaskByEntry(chat, entry);
    if (matchedTask) {
      return (
        <ToolItem
          key={`${entry.seq}-${index}`}
          tool={matchedTask}
          changeActiveChat={(task) => changeTask?.(task)}
          changePlan={changePlan}
          changeFile={changeFile}
        />
      );
    }

    return (
      <SimpleToolCard
        key={`${entry.seq}-${index}`}
        entry={entry}
        onClick={undefined}
      />
    );
  }, [changeFile, changePlan, changeTask, chat]);

  return (
    <>
      {groups.map((group, index) => {
        switch (group.type) {
          case "thought": {
            const thoughtContent = group.entries.map((entry) => entry.content || "").join("");
            return (
              <div key={`${group.type}-${group.seq}-${index}`} className="mt-6 w-full overflow-hidden rounded-2xl bg-[var(--chat-surface-soft)]/90 p-3 shadow-[var(--shadow-sm)] ring-0">
                <Reasoning isStreaming={false} defaultOpen className="not-prose mb-0">
                  <ReasoningTrigger className="rounded-xl px-2 py-1.5 hover:bg-[var(--chat-surface-muted)]/60" />
                  <ReasoningContent>{thoughtContent}</ReasoningContent>
                </Reasoning>
              </div>
            );
          }
          case "plan":
            return chat.planList?.length ? (
              <div key={`${group.type}-${group.seq}-${index}`} className="mt-6 w-full">
                <PlanSection plan={chat.planList} />
              </div>
            ) : null;
          case "task_group": {
            const taskEntry = group.entries[0];
            const toolEntries = group.entries.slice(1);
            return (
              <div key={`${group.type}-${group.seq}-${index}`} className="flex w-full">
                <div className="relative mb-2 mt-1 w-8 shrink-0 overflow-hidden">
                  <i className="font_family icon-yiwanchengtianchong absolute left-0 top-0 text-[16px] text-[#0071e3]"></i>
                </div>
                <div className="mb-2 flex-1 overflow-hidden">
                  <div className="font-[500]">{taskEntry.title}</div>
                  {toolEntries.map((entry, toolIndex) => renderToolEntry(entry, toolIndex))}
                </div>
              </div>
            );
          }
          case "conclusion":
            return chat.conclusion && index === lastConclusionIndex ? (
              <div key={`${group.type}-${group.seq}-${index}`} className="w-full">
                <ConclusionSection chat={chat} changeFile={changeFile} />
              </div>
            ) : null;
          case "tool":
          default:
            return (
              <div key={`${group.type}-${group.seq}-${index}`} className="w-full">
                {group.entries.map((entry, toolIndex) => renderToolEntry(entry, toolIndex))}
              </div>
            );
        }
      })}
    </>
  );
};

const ConclusionSection: FC<{
  chat: CHAT.ChatItem;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ chat, changeFile }) => {
  const summary =
    chat.conclusion?.resultMap?.taskSummary ||
    chat.conclusion?.result ||
    "任务已完成";
  const summaryStreaming =
    !!chat.loading && chat.conclusion?.messageType === "agent_stream";
  return (
    <div className="mb-[8px]">
      <div className="mb-[8px]">
        <MessageResponse isStreaming={summaryStreaming}>{summary}</MessageResponse>
      </div>
      <AttachmentList
        files={buildAttachment(chat.conclusion?.resultMap.fileList || [])}
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
  const hasAssistantPayload =
    !!chat.response ||
    !!thoughtText ||
    !!chat.tip ||
    !!chat.planList?.length ||
    !!chat.tasks.length ||
    !!chat.conclusion;
  const useTimelineReplay = !chat.loading && !!chat.timeline?.length;
  const showStandaloneResponse = !!chat.response && (!useTimelineReplay || !chat.conclusion);
  const [copied, setCopied] = useState(false);

  const changeActiveChat = useCallback((task: CHAT.Task) => {
    changeTask?.(task);
  }, [changeTask]);

  const handleCopy = useCallback(() => {
    if (!chat.response) return;
    navigator.clipboard.writeText(chat.response).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [chat.response]);

  return (
    <div className="flex h-full flex-col text-[14px] font-normal text-[#111827]">
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
        <div className="mt-5 w-full text-[14px] text-muted-foreground">
          {chat.tip}
        </div>
      ) : null}

      {/* AI 回复（Markdown） */}
      {showStandaloneResponse ? (
        <div className="mt-6 flex w-full justify-start">
          <Message from="assistant" className="w-full max-w-full">
            <MessageContent>
              <MessageResponse isStreaming={chat.loading}>{chat.response}</MessageResponse>
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

      {useTimelineReplay ? (
        <div className="mt-6 w-full">
          <TimelineReplay
            timeline={chat.timeline!}
            chat={chat}
            changeTask={changeTask}
            changeFile={changeFile}
            changePlan={changePlan}
          />
        </div>
      ) : (
        <>
          {/* 思考过程（深度研究模式） */}
          {!isReactType && thoughtText ? (
            <div className="mt-6 w-full overflow-hidden rounded-2xl bg-[var(--chat-surface-soft)]/90 p-3 shadow-[var(--shadow-sm)] ring-0">
              <Reasoning isStreaming={chat.loading} defaultOpen className="not-prose mb-0">
                <ReasoningTrigger className="rounded-xl px-2 py-1.5 hover:bg-[var(--chat-surface-muted)]/60" />
                <ReasoningContent>{thoughtText}</ReasoningContent>
              </Reasoning>
            </div>
          ) : null}

          {/* 任务计划 */}
          {!isReactType && chat.planList?.length ? (
            <div className="mt-6 w-full">
              <PlanSection plan={chat.planList} />
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
              <ConclusionSection chat={chat} changeFile={changeFile} />
            </div>
          ) : null}
        </>
      )}

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
