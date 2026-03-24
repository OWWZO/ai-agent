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

// 空状态动画组件
const EmptyState = () => (
  <motion.div
    className="flex h-full items-center justify-center"
    initial={{ opacity: 0, scale: 0.95 }}
    animate={{ opacity: 1, scale: 1 }}
    transition={{ duration: 0.4, ease: [0.25, 0.46, 0.45, 0.94] }}
  >
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
  </motion.div>
);

// 头部动画组件
const Header = ({
  title,
  canPreview,
}: {
  title: string;
  canPreview?: boolean;
}) => (
  <motion.div
    className="flex items-center justify-between px-4 py-3"
    initial={{ opacity: 0, y: -10 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ duration: 0.3, delay: 0.1 }}
  >
    <div className="flex min-w-0 flex-1 items-center gap-2">
      <motion.div
        initial={{ rotate: -10, opacity: 0 }}
        animate={{ rotate: 0, opacity: 1 }}
        transition={{ duration: 0.3, delay: 0.15 }}
      >
        <FileText className="h-4 w-4 shrink-0 text-[#86868b]" />
      </motion.div>
      <motion.span
        className={classNames(
          "truncate text-[13px] font-medium",
          canPreview ? "cursor-pointer text-[#0071e3] hover:underline" : "text-[#1d1d1f]"
        )}
        initial={{ opacity: 0, x: -5 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.3, delay: 0.2 }}
      >
        {title}
      </motion.span>
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
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.3 }}
      >
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 shrink-0 text-[#86868b] hover:text-[#1d1d1f]"
        >
          <Maximize2 className="h-4 w-4" />
        </Button>
      </motion.div>
    )}
  </motion.div>
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

  // Empty State
  if (!taskItem) {
    return <EmptyState />;
  }

  return (
    <motion.div
      className={classNames("flex h-full flex-col", className)}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
    >
      {/* Header */}
      <Header title={title} canPreview={canPreview} />

      <Separator className="bg-[#e8e8ed]" />

      {/* Content */}
      <motion.div
        className="flex-1 overflow-hidden"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.4, delay: 0.2 }}
      >
        <AnimatePresence mode="wait">
          <motion.div
            key={taskItem?.id || "empty"}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.3 }}
            className="h-full"
          >
            <ActionPanel
              className="h-full"
              taskItem={taskItem}
              allowShowToolBar
            />
          </motion.div>
        </AnimatePresence>
      </motion.div>

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
    </motion.div>
  );
};

export default FilePreview;
