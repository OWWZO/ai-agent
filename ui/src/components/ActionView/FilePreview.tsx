import classNames from "classnames";
import { useEffect, useMemo, useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import ActionPanel, { PanelItemType } from "../ActionPanel";
import { useMemoizedFn } from "ahooks";
import dayjs from "dayjs";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ChevronLeft, ChevronRight, Clock } from "lucide-react";
import RunStatus from "./RunStatus";
import { getPrimaryTaskFile } from "@/utils/taskArtifacts";
import {
  filterPreviewTaskList,
  resolvePreviewTaskRenderKey,
  resolvePreviewTaskSelection,
} from "./filePreviewModel";

// 空状态动画组件
const EmptyState = () => (
  <div className="flex h-full items-center justify-center">
    <Card className="w-64 border-dashed">
      <CardContent className="flex flex-col items-center justify-center py-8 text-center">
        <motion.div
          className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[#f5f5f7]"
          animate={{
            scale: [1, 1.05, 1],
            opacity: [0.8, 1, 0.8],
          }}
          transition={{
            duration: 2,
            repeat: Infinity,
            ease: "easeInOut",
          }}
        >
          <Clock className="h-5 w-5 text-[#86868b]" />
        </motion.div>
        <p className="text-sm font-medium text-[#1d1d1f]">动态</p>
        <p className="mt-1 text-xs text-[#86868b]">任务执行过程将在这里实时展示</p>
      </CardContent>
    </Card>
  </div>
);

const MissingArtifactState = ({ reason }: { reason?: string }) => (
  <div className="flex h-full items-center justify-center">
    <Card className="w-72 border-dashed bg-muted/15 shadow-none">
      <CardContent className="flex flex-col items-center justify-center py-8 text-center">
        <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[#f5f5f7]">
          <span className="text-lg text-[#86868b]">?</span>
        </div>
        <p className="text-sm font-medium text-[#1d1d1f]">引用内容不可读取</p>
        <p className="mt-1 text-xs text-[#86868b]">
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
  runState?: {
    status?: string;
    errorMsg?: string;
    finishedAt?: string;
  };
}> = ({ taskItem: defaultTaskItem, className, taskList: taskListProp, runState }) => {
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

  // Empty State
  if (!taskItem) {
    return <EmptyState />;
  }

  return (
    <div className={classNames("flex h-full flex-col", className)}>
      {/* Content — 删除内部 Header，直接上内容 */}
      <div className="flex-1 overflow-hidden">
        <div className="flex h-full flex-col">
          <RunStatus
            status={runState?.status}
            errorMsg={runState?.errorMsg}
            finishedAt={runState?.finishedAt}
            className="mx-3 mt-2 mb-1"
          />
          <div className="min-h-0 flex-1">
            {artifactMissing ? (
              <MissingArtifactState reason={primaryFile?.missingReason} />
            ) : (
              <AnimatePresence mode="sync" initial={false}>
                <motion.div
                  key={taskRenderKey}
                  initial={{
                    opacity: 0,
                    y: 8,
                  }}
                  animate={{
                    opacity: 1,
                    y: 0,
                  }}
                  exit={{
                    opacity: 0,
                    y: -6,
                  }}
                  transition={{ duration: 0.2 }}
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
            initial={{
              opacity: 0,
              y: 10,
            }}
            animate={{
              opacity: 1,
              y: 0,
            }}
            exit={{
              opacity: 0,
              y: 10,
            }}
          >
            <div className="flex items-center justify-between px-4 py-2">
              <Button
                variant="ghost"
                size="sm"
                className="h-7 gap-1 px-2 text-[13px] text-[#86868b] hover:text-[#1d1d1f] disabled:opacity-30"
                onClick={pre}
                disabled={realActiveTaskIndex <= 0}
              >
                <ChevronLeft className="h-4 w-4" />
                上一个
              </Button>

              <motion.div
                className="flex items-center gap-2 text-xs text-[#86868b]"
                key={realActiveTaskIndex}
                initial={{
                  opacity: 0,
                  scale: 0.9,
                }}
                animate={{
                  opacity: 1,
                  scale: 1,
                }}
                transition={{ duration: 0.2 }}
              >
                <Clock className="h-3 w-3" />
                <span>
                  {dayjs(+(taskList[realActiveTaskIndex]?.messageTime || 0)).format(
                    "HH:mm:ss"
                  )}
                </span>
                <span className="mx-1 text-[#e8e8ed]">|</span>
                <span>{realActiveTaskIndex + 1} / {taskLength}</span>
              </motion.div>

              <Button
                variant="ghost"
                size="sm"
                className="h-7 gap-1 px-2 text-[13px] text-[#86868b] hover:text-[#1d1d1f] disabled:opacity-30"
                onClick={next}
                disabled={realActiveTaskIndex >= taskLength - 1}
              >
                下一个
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default FilePreview;
