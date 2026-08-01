import { FC, memo, useMemo } from "react";
import { ListIcon } from "lucide-react";
import {
  diffSessionTaskTransitions,
  extractSessionTaskSnapshot,
  findPreviousSessionTasks,
} from "./sessionTaskModel";

type SessionTaskListProps = {
  tool: CHAT.Task;
  chat?: CHAT.ChatItem;
};

/**
 * 时间线内：仅渲染 todo 状态变化（完成 / 开始）。
 * 完整列表见输入框上方 SessionTaskComposerBar。
 */
const SessionTaskList: FC<SessionTaskListProps> = memo(({ tool, chat }) => {
  const current = useMemo(() => extractSessionTaskSnapshot(tool), [tool]);
  const previousTool = useMemo(
    () => findPreviousSessionTasks(chat, tool),
    [chat, tool]
  );
  const previous = useMemo(
    () => extractSessionTaskSnapshot(previousTool)?.tasks || null,
    [previousTool]
  );
  const transitions = useMemo(
    () =>
      current
        ? diffSessionTaskTransitions(previous, current.tasks)
        : [],
    [current, previous]
  );

  if (!transitions.length) {
    return null;
  }

  return (
    <div
      className="timeline-segment-enter mt-1 flex flex-col gap-1 py-0.5 pl-0.5"
      data-testid="session-task-transitions"
    >
      {transitions.map((item) => (
        <div
          key={item.id}
          className="flex min-w-0 items-center gap-2 text-[13.5px] leading-6 text-[var(--chat-text-muted)]"
        >
          <ListIcon className="size-3.5 shrink-0 opacity-70" strokeWidth={1.75} />
          <span className="min-w-0 truncate">
            {item.kind === "completed" ? "完成" : "开始"}
            <span className="text-[var(--chat-text-soft)]">「{item.label}」</span>
          </span>
        </div>
      ))}
    </div>
  );
});

SessionTaskList.displayName = "SessionTaskList";

export default SessionTaskList;
