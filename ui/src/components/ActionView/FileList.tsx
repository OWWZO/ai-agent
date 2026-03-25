import { copyText, downloadFile, formatTimestamp, showMessage } from "@/utils";
import { keyBy } from "lodash";
import React, { useMemo, useState } from "react";
import ActionViewFrame from "./ActionViewFrame";
import { FileRenderer, HTMLRenderer, PanelItemType, TableRenderer } from "../ActionPanel";
import { useBoolean, useMemoizedFn } from "ahooks";
import LoadingSpinner from "../LoadingSpinner";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  FileText,
  Download,
  Copy,
  ChevronRight,
  FileSpreadsheet,
  FileCode,
  FileIcon,
} from "lucide-react";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";

type FileItem = {
  name: string;
  messageTime?: string;
  type: string;
  task: PanelItemType;
  url: string;
};

const messageTypeEnum = ['file', 'code', 'html', 'markdown', 'result', 'data_analysis'];

const getFileIcon = (type: string) => {
  switch (type) {
    case 'csv':
    case 'xlsx':
    case 'xls':
      return <FileSpreadsheet className="h-4 w-4 text-emerald-500" />;
    case 'html':
    case 'code':
      return <FileCode className="h-4 w-4 text-blue-500" />;
    case 'md':
    case 'markdown':
    case 'txt':
      return <FileText className="h-4 w-4 text-gray-500" />;
    default:
      return <FileIcon className="h-4 w-4 text-gray-400" />;
  }
};

const FileList: React.FC<{
  taskList?: PanelItemType[];
  activeFile?: CHAT.TFile;
  clearActiveFile?: () => void;
}> = (props) => {
  const { taskList, clearActiveFile, activeFile } = props;

  const [activeItem, setActiveItem] = useState<string | undefined>();
  const [copying, { setFalse: stopCopying, setTrue: startCopying }] = useBoolean(false);

  const clearActive = useMemoizedFn(() => {
    clearActiveFile?.();
    setActiveItem(undefined);
  });

  const { list: fileList, map: fileMap } = useMemo(() => {
    let map: Record<string, FileItem> = {};
    const list = (taskList || []).reduce<FileItem[]>((pre, task) => {
      const { resultMap } = task;
      if (messageTypeEnum.includes(task.messageType)) {
        const fileInfo: FileItem[] = (resultMap?.fileInfo ?? resultMap.fileList ?? []).map((item) => {
          const extension = item.fileName?.split('.')?.pop();
          return {
            ...item,
            name: item.fileName!,
            url: item.domainUrl!,
            task,
            messageTime: formatTimestamp(task.messageTime),
            type: extension!
          };
        });
        pre.push(...fileInfo.filter((item) => !map[item.name]));
        map = keyBy(pre, 'name');
      }
      return pre;
    }, []);
    return { list, map };
  }, [taskList]);

  const fileItem = activeFile || (activeItem ? fileMap[activeItem] : undefined);

  const copy = useMemoizedFn(async () => {
    if (!fileItem?.url) return;
    startCopying();
    try {
      const response = await fetch(fileItem.url);
      if (!response.ok) throw new Error('Network response was not ok');
      const data = await response.text();
      copyText(data);
      showMessage()?.success('复制成功');
    } finally {
      stopCopying();
    }
  });

  // File List View
  if (!fileItem) {
    if (!fileList?.length) {
      return (
        <div className="flex h-full items-center justify-center">
          <Card className="w-64 bg-muted/15 py-8 shadow-none ring-0">
            <CardContent className="flex flex-col items-center justify-center py-0 text-center">
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[#f5f5f7]">
                <FileText className="h-5 w-5 text-[#86868b]" />
              </div>
              <p className="text-sm font-medium text-[#1d1d1f]">暂无文件</p>
              <p className="mt-1 text-xs text-[#86868b]">任务生成的文件将在这里显示</p>
            </CardContent>
          </Card>
        </div>
      );
    }

    return (
      <div className="h-full overflow-auto p-3">
        <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
          {fileList.map((item) => (
            <Card
              key={item.name}
              className="group cursor-pointer rounded-xl bg-transparent py-0 shadow-none ring-0 transition-all duration-200 hover:bg-muted/35"
              onClick={() => setActiveItem(item.name)}
            >
              <CardContent className="flex items-center gap-2.5 p-2.5">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-[#f5f5f7]">
                  {getFileIcon(item.type)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[12px] font-medium leading-5 text-[#1d1d1f]">
                    {item.name}
                  </p>
                  <p className="text-xs text-[#86868b]">{item.messageTime}</p>
                </div>
                <ChevronRight className="h-3.5 w-3.5 shrink-0 text-[#c7c7cc] transition-colors group-hover:text-[#86868b]" />
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    );
  }

  // File Detail View
  const renderContent = () => {
    switch (fileItem.type) {
      case 'ppt':
      case 'html':
        return <HTMLRenderer htmlUrl={fileItem.url} className="h-full" />;
      case 'csv':
      case 'xlsx':
        return <TableRenderer fileUrl={fileItem.url} fileName={fileItem.name} />;
      default:
        return <FileRenderer fileUrl={fileItem.url} fileName={fileItem.name} />;
    }
  };

  return (
    <ActionViewFrame
      className="bg-white/50"
      titleNode={
        <div className="flex items-center gap-2 min-w-0">
          {getFileIcon(fileItem.type)}
          <span className="truncate">{fileItem.name}</span>
        </div>
      }
      onClickTitle={clearActive}
    >
      <TooltipProvider>
        <div className="flex items-center justify-end gap-1 px-4 py-2">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 text-[#86868b] hover:text-[#1d1d1f]"
                onClick={() => downloadFile(fileItem.url.replace('preview', 'download'), fileItem.name)}
              >
                <Download className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <p>下载</p>
            </TooltipContent>
          </Tooltip>

          {!['xlsx', 'xls'].includes(fileItem.type) && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-[#86868b] hover:text-[#1d1d1f]"
                  onClick={copy}
                  disabled={copying}
                >
                  {copying ? (
                    <LoadingSpinner className="h-4 w-4" />
                  ) : (
                    <Copy className="h-4 w-4" />
                  )}
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>复制</p>
              </TooltipContent>
            </Tooltip>
          )}
        </div>
      </TooltipProvider>

      <Separator className="bg-[#e8e8ed]" />

      <div className="flex-1 overflow-auto p-4">
        {renderContent()}
      </div>
    </ActionViewFrame>
  );
};

export default FileList;
