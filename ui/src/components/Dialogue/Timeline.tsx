import { FC, memo, useMemo } from "react";
import { motion } from "motion/react";
import AttachmentList from "@/components/AttachmentList";
import LoadingSpinner from "@/components/LoadingSpinner";
import { buildAction, getIcon } from "@/utils/chat";
import {
  buildDeepSearchPreviewModel,
  resolveDeepSearchStage,
  shouldRenderDeepSearchPreview,
} from "@/utils/deepSearch";
import { getTaskFiles } from "@/utils/taskArtifacts";
import {
  Reasoning,
  ReasoningTrigger,
  ReasoningContent,
} from "@/components/ai-elements/reasoning";
import {
  CheckIcon,
  LoaderCircleIcon,
  FileTextIcon,
  SearchIcon,
  UserIcon,
} from "lucide-react";
import { resolveTaskSummaryText } from "./contentHelpers";
import {
  isTimelineTaskContainerCompleted,
  shouldShowTimelineGroupCompletedIcon,
} from "./timelineStatus";

type TimelineProps = {
  chat: CHAT.ChatItem;
  isPlanSolveMessage: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
};

type ToolItemProps = {
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  changePlan?: () => void;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
};

const ToolItem: FC<ToolItemProps> = memo(({
  tool,
  chat,
  changePlan,
  changeActiveChat,
  changeFile,
}) => {
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
            .filter((step) => step.status !== "completed")
            .map((step, index) => (
              <div key={`${step.goal}-${index}`}>
                <i className={`font_family ${getIcon(tool.messageType)}`}></i>
                <div>
                  <div>{actionInfo.action}</div>
                  <div>{step.goal}</div>
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
            review={(file) => changeFile?.(file, chat)}
          />
        </div>
      );
    }
    default: {
      const loadingType = ["html", "markdown", "data_analysis"];
      const deepSearchStage =
        tool.messageType === "deep_search"
          ? resolveDeepSearchStage(tool.resultMap?.messageType)
          : undefined;
      const loading =
        !tool.resultMap?.isFinal &&
        ((tool.messageType === "deep_search" &&
          (deepSearchStage === "extend" || deepSearchStage === "report")) ||
          loadingType.includes(tool.messageType));
      const isSearching =
        tool.messageType === "deep_search" &&
        deepSearchStage !== "report";
      const isSummarizing =
        tool.messageType === "deep_search" && deepSearchStage === "report";
      const isDeepSearchInline = isSearching || isSummarizing;

      return (
        <div
          className="mt-2 flex w-full max-w-full cursor-pointer items-center gap-3 rounded-xl px-1 py-2 transition-all duration-200 hover:bg-muted/35"
          onClick={() => changeActiveChat(tool, chat)}
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
  prevProps.chat === nextProps.chat &&
  prevProps.changePlan === nextProps.changePlan &&
  prevProps.changeActiveChat === nextProps.changeActiveChat &&
  prevProps.changeFile === nextProps.changeFile
);

ToolItem.displayName = "ToolItem";

const DeepSearchPreviewItem: FC<{
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
}> = memo(({ tool, chat, changeActiveChat }) => {
  const model = useMemo(() => buildDeepSearchPreviewModel(tool), [tool]);

  if (!model) {
    return null;
  }

  const clickable = model.interactive;
  const handleClick = () => {
    if (clickable) {
      changeActiveChat(tool, chat);
    }
  };

  return (
    <motion.div
      initial={{
        opacity: 0,
        y: 8,
      }}
      animate={{
        opacity: 1,
        y: 0,
      }}
      transition={{
        duration: 0.2,
        ease: [0.25, 0.46, 0.45, 0.94],
      }}
      className={[
        "mt-2 overflow-hidden rounded-2xl border border-[var(--chat-border)]/18",
        "bg-[var(--chat-surface-soft)]/72 px-4 py-3 shadow-[var(--shadow-xs)] ring-0",
        clickable
          ? "cursor-pointer transition-all duration-200 hover:bg-[var(--chat-surface-muted)]/78 hover:shadow-[var(--shadow-sm)]"
          : "",
      ].join(" ")}
      onClick={handleClick}
      onKeyDown={(event) => {
        if (!clickable) {
          return;
        }
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          handleClick();
        }
      }}
      role={clickable ? "button" : undefined}
      tabIndex={clickable ? 0 : undefined}
    >
      <div className="flex items-start gap-3">
        <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[var(--chat-surface)]/90 text-[var(--chat-text-muted)]">
          {model.loading ? (
            <LoaderCircleIcon className="size-4 animate-spin" />
          ) : (
            <SearchIcon className="size-4" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="truncate text-[14px] font-medium leading-snug tracking-[-0.01em] text-[var(--chat-text)]">
              {model.query}
            </span>
            <span className="inline-flex shrink-0 items-center rounded-full bg-[var(--chat-surface)] px-2 py-0.5 text-[11px] font-medium text-[var(--chat-text-muted)]">
              {model.statusLabel}
            </span>
          </div>
          <p className="mt-1 text-[12px] leading-relaxed text-[var(--chat-text-soft)]">
            {model.description}
          </p>
        </div>
      </div>
    </motion.div>
  );
});

DeepSearchPreviewItem.displayName = "DeepSearchPreviewItem";

const resolveDigitalEmployee = (task: CHAT.Task): string | undefined => {
  return task.children?.find((child) => child.digitalEmployee)?.digitalEmployee;
};

const TimelineContent: FC<{
  chat: CHAT.ChatItem;
  tasks: CHAT.Task[];
  isPlanSolveMessage: boolean;
  changeActiveChat: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
}> = ({ chat, tasks, isPlanSolveMessage, changeActiveChat, changePlan, changeFile }) => {
  return (
    <>
      {tasks.map((task, taskIndex) => {
        const digitalEmployee = resolveDigitalEmployee(task);
        const taskCompleted = isTimelineTaskContainerCompleted(task);
        return (
          <div
            key={task.id || task.messageId || task.taskId || taskIndex}
            className="overflow-hidden"
          >
            {isPlanSolveMessage && task.task ? (
              <div className="mb-1">
                <div className="font-[500]">{task.task}</div>
                {digitalEmployee && (
                  <div className="mt-1.5 inline-flex items-center gap-2 rounded-lg border border-[var(--chat-border)]/18 bg-[var(--chat-surface)]/80 px-3 py-1.5 text-[13px]">
                    <UserIcon className="h-3.5 w-3.5 text-[var(--chat-text-muted)]" />
                    <span className="text-[var(--chat-text-soft)]">{digitalEmployee}</span>
                    {taskCompleted && (
                      <>
                        <span className="text-[var(--chat-border)]">|</span>
                        <CheckIcon className="h-3.5 w-3.5 text-green-500" />
                      </>
                    )}
                  </div>
                )}
              </div>
            ) : null}
            {(task.children || []).map((tool, index) => {
              const stage =
                tool.messageType === "deep_search"
                  ? resolveDeepSearchStage(tool.resultMap?.messageType)
                  : undefined;
              const shouldRenderPreview =
                tool.messageType === "deep_search" &&
                shouldRenderDeepSearchPreview(stage);

              return (
                <div
                  key={tool.id || tool.messageId || tool.taskId || index}
                  className="overflow-hidden"
                >
                  {shouldRenderPreview ? (
                    <DeepSearchPreviewItem
                      tool={tool}
                      chat={chat}
                      changeActiveChat={changeActiveChat}
                    />
                  ) : (
                    <ToolItem
                      tool={tool}
                      chat={chat}
                      changePlan={changePlan}
                      changeActiveChat={changeActiveChat}
                      changeFile={changeFile}
                    />
                  )}
                </div>
              );
            })}
          </div>
        );
      })}
    </>
  );
};

export const Timeline: FC<TimelineProps> = ({
  chat,
  isPlanSolveMessage,
  changeActiveChat,
  changePlan,
  changeFile,
}) => (
  <>
    {chat.tasks.map((tasks, index) => {
      const lastTask = index === chat.tasks.length - 1;
      const groupKey = tasks[0]?.id || tasks[0]?.messageId || tasks[0]?.taskId || index;
      const showCompletedIcon = shouldShowTimelineGroupCompletedIcon({
        isPlanSolve: isPlanSolveMessage,
        isLastGroup: lastTask,
        loading: chat.loading,
        tasks,
      });

      return (
        <div className="flex w-full" key={groupKey}>
          {isPlanSolveMessage ? (
            <div className="relative mb-2 mt-1 w-8 shrink-0 overflow-hidden">
              {lastTask && chat.loading ? (
                <div aria-label="timeline-loading">
                  <LoadingSpinner />
                </div>
              ) : showCompletedIcon ? (
                <i
                  aria-label="timeline-completed"
                  className="font_family icon-yiwanchengtianchong absolute left-0 top-0 text-[16px] text-[#0071e3]"
                ></i>
              ) : null}
            </div>
          ) : null}
          <div className="mb-2 flex-1 overflow-hidden">
            <TimelineContent
              chat={chat}
              tasks={tasks}
              isPlanSolveMessage={isPlanSolveMessage}
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

Timeline.displayName = "Timeline";
