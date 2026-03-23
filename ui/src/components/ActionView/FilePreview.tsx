import classNames from "classnames";
import { useEffect, useMemo, useState } from "react";
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
    return (
      <div className="flex h-full items-center justify-center">
        <Card className="w-64 border-dashed">
          <CardContent className="flex flex-col items-center justify-center py-8 text-center">
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[#f5f5f7]">
              <Clock className="h-5 w-5 text-[#86868b]" />
            </div>
            <p className="text-sm font-medium text-[#1d1d1f]">实时跟随</p>
            <p className="mt-1 text-xs text-[#86868b]">任务执行过程将在这里实时展示</p>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className={classNames("flex h-full flex-col", className)}>
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3">
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <FileText className="h-4 w-4 shrink-0 text-[#86868b]" />
          <span
            className={classNames(
              "truncate text-[13px] font-medium",
              canPreview ? "cursor-pointer text-[#0071e3] hover:underline" : "text-[#1d1d1f]"
            )}
          >
            {title}
          </span>
          {canPreview && (
            <Badge variant="secondary" className="ml-2 h-4 shrink-0 text-[10px]">
              可预览
            </Badge>
          )}
        </div>
        {canPreview && (
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 shrink-0 text-[#86868b] hover:text-[#1d1d1f]"
          >
            <Maximize2 className="h-4 w-4" />
          </Button>
        )}
      </div>

      <Separator className="bg-[#e8e8ed]" />

      {/* Content */}
      <div className="flex-1 overflow-hidden">
        <ActionPanel
          className="h-full"
          taskItem={taskItem}
          allowShowToolBar
        />
      </div>

      {/* Footer Navigation */}
      {!!taskLength && taskLength > 1 && (
        <>
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

            <div className="flex items-center gap-2 text-xs text-[#86868b]">
              <Clock className="h-3 w-3" />
              <span>
                {dayjs(+(taskList?.[realActiveTaskIndex]?.messageTime || 0)).format(
                  "HH:mm:ss"
                )}
              </span>
              <span className="mx-1 text-[#e8e8ed]">|</span>
              <span>{realActiveTaskIndex + 1} / {taskLength}</span>
            </div>

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
        </>
      )}
    </div>
  );
};

export default FilePreview;
