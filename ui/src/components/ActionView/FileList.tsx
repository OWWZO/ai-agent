import { copyText, downloadFile, showMessage } from "@/utils";
import React, { memo, useMemo, useState } from "react";
import ActionViewFrame from "./ActionViewFrame";
import {
  FileRenderer,
  HTMLRenderer,
  ImageRenderer,
  PanelItemType,
  PdfRenderer,
  TableRenderer,
  WordRenderer,
} from "../ActionPanel";
import DocumentFallback from "../ActionPanel/DocumentFallback";
import { useBoolean, useMemoizedFn } from "ahooks";
import LoadingSpinner from "../LoadingSpinner";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  isBinaryPreviewFileLike,
  isDocxFileLike,
  isImageFileLike,
  isLegacyDocFileLike,
  isPdfFileLike,
  isTextCopyableFileLike,
} from "@/utils/taskArtifacts";
import {
  FileText,
  Download,
  Copy,
  FileSpreadsheet,
  FileCode,
  FileIcon,
} from "lucide-react";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import {
  buildWorkspaceTree,
  collectWorkspaceFiles,
  workspaceFileKey,
  type WorkspaceFileItem,
} from "./workspaceFiles";
import WorkspaceFileTree from "./WorkspaceFileTree";

type FileItem = WorkspaceFileItem;

const getFileIcon = (type: string) => {
  switch (type) {
    case "csv":
    case "xlsx":
    case "xls":
      return <FileSpreadsheet className="h-4 w-4 text-emerald-500" />;
    case "html":
    case "code":
      return <FileCode className="h-4 w-4 text-blue-500" />;
    case "pdf":
      return <FileText className="h-4 w-4 text-red-500" />;
    case "doc":
    case "docx":
      return <FileText className="h-4 w-4 text-blue-600" />;
    case "md":
    case "markdown":
    case "txt":
      return <FileText className="h-4 w-4 text-gray-500" />;
    default:
      return <FileIcon className="h-4 w-4 text-gray-400" />;
  }
};

const FileList: React.FC<{
  taskList?: PanelItemType[];
  activeFile?: CHAT.TFile;
  clearActiveFile?: () => void;
  /** 嵌入工作区浏览器时隐藏二级标题栏 */
  embedded?: boolean;
  /** 强制按源码视图打开（用于工作区「源码」分段） */
  forceSource?: boolean;
}> = memo((props) => {
  const {
    taskList,
    clearActiveFile,
    activeFile,
    embedded = false,
    forceSource = false,
  } = props;

  const [activeItem, setActiveItem] = useState<string | undefined>();
  const [copying, { setFalse: stopCopying, setTrue: startCopying }] = useBoolean(false);

  const clearActive = useMemoizedFn(() => {
    clearActiveFile?.();
    setActiveItem(undefined);
  });

  const { list: fileList, map: fileMap } = useMemo(() => {
    const list = collectWorkspaceFiles(taskList);
    const map = Object.fromEntries(
      list.map((item) => [workspaceFileKey(item), item])
    ) as Record<string, FileItem>;
    return {
      list,
      map,
    };
  }, [taskList]);

  const fileItem = activeFile || (activeItem ? fileMap[activeItem] : undefined);
  const missing = !!fileItem && "missing" in fileItem && Boolean(fileItem.missing);
  const missingReason =
    fileItem && "missingReason" in fileItem ? fileItem.missingReason : undefined;
  const downloadUrl =
    fileItem && "downloadUrl" in fileItem ? fileItem.downloadUrl : undefined;
  const isImageFile = Boolean(fileItem && isImageFileLike(fileItem));
  const canCopyText = Boolean(fileItem && isTextCopyableFileLike(fileItem));
  const isBinaryFile = Boolean(fileItem && isBinaryPreviewFileLike(fileItem));

  const copy = useMemoizedFn(async () => {
    if (!fileItem?.url || !canCopyText) return;
    startCopying();
    try {
      const response = await fetch(fileItem.url);
      if (!response.ok) throw new Error("Network response was not ok");
      const data = await response.text();
      copyText(data);
      showMessage()?.success("复制成功");
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
        <WorkspaceFileTree
          nodes={buildWorkspaceTree(fileList)}
          selectedFileKey={activeItem}
          onSelectFile={(file) => setActiveItem(workspaceFileKey(file))}
        />
      </div>
    );
  }

  // File Detail View
  if (missing) {
    return (
      <div className="flex h-full items-center justify-center">
        <Card className="w-72 bg-muted/15 py-8 shadow-none ring-0">
          <CardContent className="flex flex-col items-center justify-center py-0 text-center">
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[#f5f5f7]">
              <FileText className="h-5 w-5 text-[#86868b]" />
            </div>
            <p className="text-sm font-medium text-[#1d1d1f]">文件不可读取</p>
            <p className="mt-1 text-xs text-[#86868b]">
              {missingReason || "引用资源不存在或已失效"}
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  // File Detail View — 与 ActionPanel / useMsgTypes 同一套类型分支
  const renderContent = () => {
    const nameExt = (fileItem.name?.split(".").pop() || "").toLowerCase();
    const rawType = (fileItem.type || "").toLowerCase();
    const resolvedExt = resolvePreviewExtension(nameExt, rawType);

    if (isImageFile) {
      return (
        <ImageRenderer
          imageUrl={fileItem.url}
          fileName={fileItem.name}
          missingReason={missingReason}
          className="h-full"
        />
      );
    }

    if (isPdfFileLike(fileItem)) {
      return (
        <PdfRenderer
          fileUrl={fileItem.url}
          fileName={fileItem.name}
          downloadUrl={downloadUrl || fileItem.url}
          missingReason={missingReason}
          hideChrome={embedded}
          className="h-full"
        />
      );
    }

    if (isLegacyDocFileLike(fileItem) || isDocxFileLike(fileItem)) {
      return (
        <WordRenderer
          fileUrl={fileItem.url}
          fileName={fileItem.name}
          downloadUrl={downloadUrl || fileItem.url}
          missingReason={missingReason}
          legacyOnly={isLegacyDocFileLike(fileItem)}
          hideChrome={embedded}
          className="h-full"
        />
      );
    }

    // HTML：预览 = iframe；源码分段 = FileRenderer 高亮
    if ((resolvedExt === "html" || resolvedExt === "htm") && !forceSource) {
      return (
        <HTMLRenderer
          htmlUrl={fileItem.url}
          downloadUrl={downloadUrl || fileItem.url}
          missingReason={missingReason}
          className="h-full min-h-[480px]"
        />
      );
    }

    if ((resolvedExt === "html" || resolvedExt === "htm") && forceSource) {
      return (
        <FileRenderer
          fileUrl={fileItem.url}
          fileName={fileItem.name?.endsWith(".html") || fileItem.name?.endsWith(".htm")
            ? fileItem.name
            : `${fileItem.name || "preview"}.html`}
          missingReason={missingReason}
          forceSource
        />
      );
    }

    switch (resolvedExt) {
      case "ppt":
      case "pptx":
        return (
          <DocumentFallback
            label="PPT"
            title="暂不支持在线预览 PPT/PPTX"
            description="请下载后用 PowerPoint / WPS 打开。若产物已转为 HTML 演示页，请打开对应 .html 文件。"
            fileName={fileItem.name}
            downloadUrl={downloadUrl || fileItem.url}
            className="h-full"
            type="info"
          />
        );
      case "csv":
      case "xlsx":
      case "xls":
        return (
          <TableRenderer
            fileUrl={fileItem.url}
            fileName={fileItem.name}
            downloadUrl={downloadUrl || fileItem.url}
            missingReason={missingReason}
            className="h-full min-h-0"
          />
        );
      default:
        return (
          <FileRenderer
            fileUrl={fileItem.url}
            fileName={fileItem.name}
            downloadUrl={downloadUrl || fileItem.url}
            missingReason={missingReason}
          />
        );
    }
  };

  if (embedded) {
    const ext = (fileItem.type || fileItem.name?.split(".").pop() || "").toLowerCase();
    const isSpreadsheet = ["csv", "xlsx", "xls"].includes(ext);
    const isDownloadOnly = ["ppt", "pptx", "pps", "ppsx"].includes(ext);
    const isMedia =
      isImageFile ||
      isPdfFileLike(fileItem) ||
      isSpreadsheet ||
      isDownloadOnly ||
      ["html", "htm"].includes(ext);
    // 源码类：全幅白底 + 行号高亮，不套文档卡片
    const isSourceCode = [
      "py",
      "python",
      "js",
      "ts",
      "tsx",
      "jsx",
      "css",
      "scss",
      "less",
      "json",
      "xml",
      "yml",
      "yaml",
      "sh",
      "bash",
      "sql",
      "java",
      "go",
      "rs",
      "c",
      "cpp",
      "h",
      "hpp",
      "code",
      "log",
    ].includes(ext);
    const contentShellClass = isSpreadsheet
      ? "min-h-0 flex-1 overflow-hidden bg-white"
      : isMedia || isSourceCode
        ? "min-h-0 flex-1 overflow-auto bg-white"
        : "mx-auto min-h-0 w-full max-w-[720px] flex-1 overflow-auto rounded-2xl border border-[var(--chat-border)]/70 bg-white p-6 shadow-[0_1px_2px_oklch(0%_0_0_/_0.03)] sm:p-8";

    return (
      <div className="flex h-full min-h-0 flex-col">
        <div className={contentShellClass}>{renderContent()}</div>
      </div>
    );
  }

  const detailExt = (
    fileItem.type ||
    fileItem.name?.split(".").pop() ||
    ""
  ).toLowerCase();
  const isSpreadsheetDetail = ["csv", "xlsx", "xls"].includes(detailExt);

  return (
    <ActionViewFrame
      className={isSpreadsheetDetail ? "flex min-h-0 flex-col overflow-hidden bg-white/50" : "bg-white/50"}
      titleNode={
        <div className="flex min-w-0 items-center gap-2">
          {getFileIcon(fileItem.type)}
          <span className="truncate">{fileItem.name}</span>
        </div>
      }
      onClickTitle={clearActive}
    >
      <div className={isSpreadsheetDetail ? "flex h-full min-h-0 flex-col" : undefined}>
        <TooltipProvider>
          <div className="flex shrink-0 items-center justify-end gap-1 px-4 py-2">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-[#86868b] hover:text-[#1d1d1f]"
                  onClick={() =>
                    downloadFile(
                      downloadUrl || fileItem.url || "",
                      fileItem.name
                    )
                  }
                >
                  <Download className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>下载</p>
              </TooltipContent>
            </Tooltip>

            {canCopyText && !isBinaryFile && (
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

        <Separator className="shrink-0 bg-[#e8e8ed]" />

        <div
          className={
            isSpreadsheetDetail
              ? "min-h-0 flex-1 overflow-hidden"
              : "h-full overflow-auto p-4"
          }
        >
          {renderContent()}
        </div>
      </div>
    </ActionViewFrame>
  );
});

function resolvePreviewExtension(nameExt: string, rawType: string) {
  if (nameExt && nameExt.length <= 8 && !nameExt.includes("/")) {
    return nameExt;
  }
  if (rawType.includes("html")) return "html";
  if (rawType.includes("pdf")) return "pdf";
  if (rawType.includes("/")) {
    const leaf = rawType.split("/").pop() || "";
    return leaf === "plain" ? "txt" : leaf;
  }
  return rawType.replace(/^\./, "");
}

FileList.displayName = "FileList";

export default FileList;
