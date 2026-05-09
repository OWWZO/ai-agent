import classNames from "classnames";
import { useEffect, useMemo, useState, type ReactNode } from "react";
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
  Search,
} from "lucide-react";
import RunStatus from "./RunStatus";
import { getPrimaryTaskFile } from "@/utils/taskArtifacts";
import {
  filterPreviewTaskList,
  resolvePreviewCanPreview,
  resolvePreviewLeadingIcon,
  resolvePreviewTaskRenderKey,
  resolvePreviewTaskSelection,
  resolvePreviewTitle,
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

// 头部动画组件
const Header = ({
  title,
  canPreview,
  leadingIcon,
}: {
  title: string;
  canPreview?: boolean;
  leadingIcon?: ReactNode;
}) => (
  <div className="flex items-center justify-between px-4 py-3">
    <div className="flex min-w-0 flex-1 items-center gap-2">
      <div>
        {leadingIcon ?? (
          <FileText className="h-4 w-4 shrink-0 text-[#86868b]" strokeWidth={1.75} />
        )}
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
            initial={{
              opacity: 0,
              scale: 0.8
            }}
            animate={{
              opacity: 1,
              scale: 1
            }}
            exit={{
              opacity: 0,
              scale: 0.8
            }}
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

const MissingArtifactState = ({ reason }: { reason?: string }) => (
  <div className="flex h-full items-center justify-center">
    <Card className="w-72 border-dashed bg-muted/15 shadow-none">
      <CardContent className="flex flex-col items-center justify-center py-8 text-center">
        <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[#f5f5f7]">
          <FileText className="h-5 w-5 text-[#86868b]" />
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

  const { useFile, useHtml, useExcel, useImage } = useMsgTypes(taskItem) || {};
  const primaryFile = useMemo(() => getPrimaryTaskFile(taskItem), [taskItem]);
  const artifactMissing = Boolean(primaryFile?.missing);

  const title = useMemo(() => {
    return resolvePreviewTitle(taskItem, primaryFile);
  }, [primaryFile, taskItem]);

  const leadingIconType = useMemo(() => resolvePreviewLeadingIcon(taskItem), [taskItem]);
  const headerLeadingIcon = useMemo(() => {
    if (leadingIconType !== "search") {
      return undefined;
    }
    return <Search className="h-4 w-4 shrink-0 text-[#86868b]" strokeWidth={1.75} />;
  }, [leadingIconType]);

  const canPreview = useMemo(() => {
    return resolvePreviewCanPreview(
      {
        useFile,
        useHtml,
        useExcel,
        useImage,
      },
      artifactMissing
    );
  }, [artifactMissing, useExcel, useFile, useHtml, useImage]);
  const taskRenderKey = useMemo(() => resolvePreviewTaskRenderKey(taskItem), [taskItem]);

  // Empty State
  if (!taskItem) {
    return <EmptyState />;
  }

  return (
    <div className={classNames("flex h-full flex-col", className)}>
      {/* Header */}
      <Header title={title} canPreview={canPreview} leadingIcon={headerLeadingIcon} />

      <Separator className="bg-[#e8e8ed]" />

      {/* Content */}
      <div className="flex-1 overflow-hidden">
        <div className="flex h-full flex-col">
          <RunStatus
            status={runState?.status}
            errorMsg={runState?.errorMsg}
            finishedAt={runState?.finishedAt}
            className="mx-4 mt-4 mb-2"
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
                    y: 8
                  }}
                  animate={{
                    opacity: 1,
                    y: 0
                  }}
                  exit={{
                    opacity: 0,
                    y: -6
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

      {/* Footer Navigation */}
      <AnimatePresence>
        {!!taskLength && taskLength > 1 && (
          <motion.div
            initial={{
              opacity: 0,
              y: 10
            }}
            animate={{
              opacity: 1,
              y: 0
            }}
            exit={{
              opacity: 0,
              y: 10
            }}
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
                initial={{
                  opacity: 0,
                  scale: 0.9
                }}
                animate={{
                  opacity: 1,
                  scale: 1
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
