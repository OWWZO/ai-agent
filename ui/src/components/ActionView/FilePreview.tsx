import classNames from "classnames";
import { useEffect, useMemo, useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import ActionPanel, { PanelItemType, useMsgTypes } from "../ActionPanel";
import { useMemoizedFn } from "ahooks";
import dayjs from "dayjs";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import {
  ChevronLeft,
  ChevronRight,
  Clock,
  FileText,
  Maximize2,
} from "lucide-react";

const getStableTaskRenderKey = (taskItem?: CHAT.Task | PanelItemType) => {
  if (!taskItem) {
    return "empty";
  }

  // 优先使用流式前后都稳定不变的标识，避免同一任务被误判为新内容导致面板闪烁。
  return (
    taskItem.messageId ||
    (taskItem.taskId && taskItem.messageTime
      ? `${taskItem.taskId}:${taskItem.messageTime}`
      : undefined) ||
    taskItem.id ||
    "empty"
  );
};

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
        <p className="text-sm font-medium text-[#1d1d1f]">实时跟随</p>
        <p className="mt-1 text-xs text-[#86868b]">任务执行过程将在这里实时展示</p>
      </CardContent>
    </Card>
  </div>
);

// 头部动画组件
const Header = ({
  title,
  canPreview,
}: {
  title: string;
  canPreview?: boolean;
}) => (
  <div className="flex items-center justify-between px-4 py-3">
    <div className="flex min-w-0 flex-1 items-center gap-2">
      <div>
        <FileText className="h-4 w-4 shrink-0 text-[#86868b]" />
      </div>
      <span
        className={classNames(
          "truncate text-[13px] font-medium",
          canPreview ? "cursor-pointer text-[#0071e3] hover:underline" : "text-[#1d1d1f]"
        )}
      >
        {title}
      </span>
      <AnimatePresence>
        {canPreview && (
          <motion.div
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.8 }}
          >
            <Badge variant="secondary" className="ml-2 h-4 shrink-0 text-[10px]">
              可预览
            </Badge>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
    {canPreview && (
      <div>
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 shrink-0 text-[#86868b] hover:text-[#1d1d1f]"
        >
          <Maximize2 className="h-4 w-4" />
        </Button>
      </div>
    )}
  </div>
);

const FilePreview: React.FC<{
  taskItem?: CHAT.Task;
  taskList?: PanelItemType[];
  className?: string;
}> = ({ taskItem: defaultTaskItem, className, taskList: taskListProp }) => {
  const taskList = useMemo(() => {
    return taskListProp?.filter(
      (item) => !["task_summary", "result"].includes(item.messageType)
    );
  }, [taskListProp]);

  const [curActiveTaskIndex, setCurActiveTaskIndex] = useState<number | undefined>();

  let taskItem =
    typeof curActiveTaskIndex === "number"
      ? taskList?.[curActiveTaskIndex] || defaultTaskItem
      : defaultTaskItem;

  if (!taskItem) {
    taskItem = taskList?.[taskList.length - 1];
  }

  useEffect(() => {
    if (defaultTaskItem) {
      setCurActiveTaskIndex(undefined);
    }
  }, [defaultTaskItem]);

  const realActiveTaskIndex = useMemo(() => {
    const index = taskList?.findIndex((item) => item.id === taskItem?.id);
    return index || 0;
  }, [taskItem?.id, taskList]);

  const taskLength = taskList?.length || 0;

  const next = useMemoizedFn(() => {
    setCurActiveTaskIndex(Math.min(taskLength - 1, realActiveTaskIndex + 1));
  });

  const pre = useMemoizedFn(() => {
    setCurActiveTaskIndex(Math.max(0, realActiveTaskIndex - 1));
  });

  const { useHtml, useExcel } = useMsgTypes(taskItem) || {};

  const title = useMemo(() => {
    if (!taskItem) return "";
    const { messageType, resultMap } = taskItem;
    if (messageType === "tool_result") {
      return taskItem.toolResult?.toolName || "工具执行";
    }
    if (messageType === "file" || messageType === "html") {
      const [fileInfo] = resultMap?.fileInfo || [];
      return fileInfo?.fileName || messageType;
    }
    if (messageType === "deep_search" && resultMap?.messageType === "report") {
      return resultMap?.query || "深度搜索";
    }
    return messageType;
  }, [taskItem]);

  const canPreview = useHtml || useExcel;
  const taskRenderKey = useMemo(() => getStableTaskRenderKey(taskItem), [taskItem]);

  // Empty State
  if (!taskItem) {
    return <EmptyState />;
  }

  return (
    <div className={classNames("flex h-full flex-col", className)}>
      {/* Header */}
      <Header title={title} canPreview={canPreview} />

      <Separator className="bg-[#e8e8ed]" />

      {/* Content */}
      <div className="flex-1 overflow-hidden">
        <AnimatePresence mode="sync" initial={false}>
          <motion.div
            key={taskRenderKey}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
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
      </div>

      {/* Footer Navigation */}
      <AnimatePresence>
        {!!taskLength && taskLength > 1 && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 10 }}
          >
            <Separator className="bg-[#e8e8ed]" />
            <div className="flex items-center justify-between px-4 py-3">
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
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.2 }}
              >
                <Clock className="h-3 w-3" />
                <span>
                  {dayjs(+(taskList?.[realActiveTaskIndex]?.messageTime || 0)).format(
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
