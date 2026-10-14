import classNames from "classnames";
import { memo, useEffect, useMemo, useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import ActionPanel, { PanelItemType } from "../ActionPanel";
import { useMemoizedFn } from "ahooks";
import dayjs from "dayjs";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ChevronLeft, ChevronRight, Clock } from "lucide-react";
import { getPrimaryTaskFile } from "@/utils/taskArtifacts";
import { EASE_OUT, useMotionConfig } from "@/lib/motion";
import {
  filterPreviewTaskList,
  resolvePreviewTaskRenderKey,
  resolvePreviewTaskSelection,
} from "./filePreviewModel";

// 空状态动画组件
const EmptyState = () => {
  const { reduce, loop } = useMotionConfig();

  return (
    <div className="flex h-full items-center justify-center">
      <Card className="w-64 border-dashed">
        <CardContent className="flex flex-col items-center justify-center py-8 text-center">
          <motion.div
            className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-hover"
            animate={reduce ? { opacity: 1 } : { opacity: [0.75, 1, 0.75] }}
            transition={
              reduce
                ? { duration: 0 }
                : {
                  duration: 2,
                  repeat: loop > 0 ? loop : 0,
                  ease: "easeInOut",
                }
            }
          >
            <Clock className="h-5 w-5 text-foreground-subtle" />
          </motion.div>
          <p className="text-ui-base font-medium text-foreground">动态</p>
          <p className="mt-1 text-ui-sm text-foreground-subtle">任务执行过程将在这里实时展示</p>
        </CardContent>
      </Card>
    </div>
  );
};

const MissingArtifactState = ({ reason }: { reason?: string }) => (
  <div className="flex h-full items-center justify-center">
    <Card className="w-72 border-dashed bg-muted/15 shadow-none">
      <CardContent className="flex flex-col items-center justify-center py-8 text-center">
        <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-hover">
          <span className="text-ui-lg text-foreground-subtle">?</span>
        </div>
        <p className="text-ui-base font-medium text-foreground">引用内容不可读取</p>
        <p className="mt-1 text-ui-sm text-foreground-subtle">
          {reason || "引用资源不存在或已失效"}
        </p>
      </CardContent>
    </Card>
  </div>
);

const FilePreview: React.FC<{
  taskItem?: CHAT.Task;
  taskList?: PanelItemType[];
  className?: string;
}> = memo(({ taskItem: defaultTaskItem, className, taskList: taskListProp }) => {
  const taskList = useMemo(() => {
    return filterPreviewTaskList(taskListProp);
  }, [taskListProp]);

  const [curActiveTaskIndex, setCurActiveTaskIndex] = useState<number | undefined>();

  useEffect(() => {
    if (defaultTaskItem) {
      setCurActiveTaskIndex(undefined);
    }
  }, [defaultTaskItem]);

  const { taskItem, realActiveTaskIndex, taskLength } = useMemo(() => {
    return resolvePreviewTaskSelection({
      defaultTaskItem,
      taskList,
      activeTaskIndex: curActiveTaskIndex,
    });
  }, [curActiveTaskIndex, defaultTaskItem, taskList]);

  const next = useMemoizedFn(() => {
    setCurActiveTaskIndex(Math.min(taskLength - 1, realActiveTaskIndex + 1));
  });

  const pre = useMemoizedFn(() => {
    setCurActiveTaskIndex(Math.max(0, realActiveTaskIndex - 1));
  });

  const primaryFile = useMemo(() => getPrimaryTaskFile(taskItem), [taskItem]);
  const artifactMissing = Boolean(primaryFile?.missing);
  const taskRenderKey = useMemo(() => resolvePreviewTaskRenderKey(taskItem), [taskItem]);
  const { reduce } = useMotionConfig();
  const swapTransition = {
    duration: 0.18,
    ease: EASE_OUT,
  } as const;

  // Empty State
  if (!taskItem) {
    return <EmptyState />;
  }

  return (
    <div className={classNames("flex h-full flex-col", className)}>
      {/* Content — 删除内部 Header，直接上内容 */}
      <div className="flex-1 overflow-hidden">
        <div className="flex h-full flex-col">
          <div className="min-h-0 flex-1">
            {artifactMissing ? (
              <MissingArtifactState reason={primaryFile?.missingReason} />
            ) : (
              <AnimatePresence mode="wait" initial={false}>
                <motion.div
                  key={taskRenderKey}
                  initial={reduce ? { opacity: 0 } : {
                    opacity: 0,
                    y: 6
                  }}
                  animate={{
                    opacity: 1,
                    y: 0
                  }}
                  exit={reduce ? { opacity: 0 } : {
                    opacity: 0,
                    y: -4
                  }}
                  transition={swapTransition}
                  className="h-full"
                >
                  <ActionPanel
                    className="h-full"
                    taskItem={taskItem}
                    allowShowToolBar
                  />
                </motion.div>
              </AnimatePresence>
            )}
          </div>
        </div>
      </div>

      {/* Footer Navigation — 仅当有多页时才显示 */}
      <AnimatePresence>
        {!!taskLength && taskLength > 1 && (
          <motion.div
            initial={reduce ? { opacity: 0 } : {
              opacity: 0,
              y: 8
            }}
            animate={{
              opacity: 1,
              y: 0
            }}
            exit={reduce ? { opacity: 0 } : {
              opacity: 0,
              y: 8
            }}
            transition={swapTransition}
          >
            {/* ZCode pptx-preview-viewer.tsx:843 —— 底部工具条：min-h-10 + border-t +
                bg-surface/30，内容居中；翻页走 ghost 图标按钮 + tabular-nums 数字。
                时间戳保留在左端（ZCode 无此信息），用等宽 spacer 让翻页组保持居中。 */}
            <div className="flex min-h-10 shrink-0 flex-wrap items-center justify-center gap-1 border-t border-border bg-surface/30 px-2 py-1.5 text-ui-base">
              <motion.span
                className="mr-auto flex items-center gap-1 text-ui-xs text-foreground-subtlest"
                key={realActiveTaskIndex}
                initial={reduce ? { opacity: 0 } : {
                  opacity: 0,
                  scale: 0.96
                }}
                animate={{
                  opacity: 1,
                  scale: 1
                }}
                transition={swapTransition}
              >
                <Clock className="size-3" />
                <span className="tabular-nums">
                  {dayjs(+(taskList[realActiveTaskIndex]?.messageTime || 0)).format(
                    "HH:mm:ss"
                  )}
                </span>
              </motion.span>

              <Button
                variant="ghost"
                size="icon-sm"
                className="text-foreground-subtle hover:text-foreground disabled:opacity-30"
                aria-label="上一个"
                title="上一个"
                onClick={pre}
                disabled={realActiveTaskIndex <= 0}
              >
                <ChevronLeft className="size-4" />
              </Button>

              <span className="w-12 text-center tabular-nums text-foreground">
                {realActiveTaskIndex + 1}
              </span>
              <span className="min-w-10 text-center tabular-nums text-foreground-subtle">
                / {taskLength}
              </span>

              <Button
                variant="ghost"
                size="icon-sm"
                className="text-foreground-subtle hover:text-foreground disabled:opacity-30"
                aria-label="下一个"
                title="下一个"
                onClick={next}
                disabled={realActiveTaskIndex >= taskLength - 1}
              >
                <ChevronRight className="size-4" />
              </Button>

              <span className="ml-auto" aria-hidden="true" />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
});

FilePreview.displayName = "FilePreview";

export default FilePreview;
