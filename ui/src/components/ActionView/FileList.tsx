import { copyText, downloadFile, showMessage } from "@/utils";
import React, { memo, useMemo, useState } from "react";
import ActionViewFrame from "./ActionViewFrame";
import { FilePreviewSwitch, PanelItemType } from "../ActionPanel";
import { useBoolean, useMemoizedFn } from "ahooks";
import LoadingSpinner from "../LoadingSpinner";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { resolveFilePreviewKind } from "@/lib/filePreview/kinds";
import {
  isBinaryPreviewFileLike,
  isImageFileLike,
  isModel3DFileLike,
  isPdfFileLike,
  isPptFileLike,
  isTextCopyableFileLike,
} from "@/utils/taskArtifacts";
import {
  FileText,
  Download,
  Copy,
  FileSpreadsheet,
  FileCode,
  FileIcon,
  Presentation,
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

// 类型色统一走语义 token（ZCode 迁移要求：禁写死 hex / Tailwind 调色板）。
const getFileIcon = (type: string) => {
  switch (type) {
    case "csv":
    case "xlsx":
    case "xls":
      return <FileSpreadsheet className="h-4 w-4 text-success" />;
    case "html":
    case "code":
      return <FileCode className="h-4 w-4 text-brand" />;
    case "pdf":
      return <FileText className="h-4 w-4 text-destructive" />;
    case "ppt":
    case "pptx":
    case "pps":
    case "ppsx":
      return <Presentation className="h-4 w-4 text-warning" />;
    case "doc":
    case "docx":
      return <FileText className="h-4 w-4 text-brand" />;
    case "md":
    case "markdown":
    case "txt":
      return <FileText className="h-4 w-4 text-foreground-subtle" />;
    default:
      return <FileIcon className="h-4 w-4 text-foreground-subtlest" />;
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
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-hover">
                <FileText className="h-5 w-5 text-foreground-subtle" />
              </div>
              <p className="text-sm font-medium text-foreground">暂无文件</p>
              <p className="mt-1 text-xs text-foreground-subtle">任务生成的文件将在这里显示</p>
            </CardContent>
          </Card>
        </div>
      );
    }

    return (
      <div className="h-full overflow-auto p-2">
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
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-hover">
              <FileText className="h-5 w-5 text-foreground-subtle" />
            </div>
            <p className="text-sm font-medium text-foreground">文件不可读取</p>
            <p className="mt-1 text-xs text-foreground-subtle">
              {missingReason || "引用资源不存在或已失效"}
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  // File Detail View — 类型分派统一交给 FilePreviewSwitch（全项目唯一分派点）
  const renderContent = () => (
    <FilePreviewSwitch
      fileUrl={fileItem.url}
      fileName={fileItem.name}
      mimeType={fileItem.type}
      downloadUrl={downloadUrl || fileItem.url}
      missingReason={missingReason}
      forceSource={forceSource}
      embedded={embedded}
    />
  );

  if (embedded) {
    const ext = (fileItem.type || fileItem.name?.split(".").pop() || "").toLowerCase();
    const isSpreadsheet = ["csv", "xlsx", "xls"].includes(ext);
    const isDownloadOnly = ["ppt", "pptx", "pps", "ppsx"].includes(ext);
    // 这些渲染器自带 ViewerPanelShell（含顶栏与滚动容器），外壳必须让位，否则会套出双层边框。
    const isSelfScrollingPreview =
      (isPptFileLike(fileItem) && !forceSource && Boolean(fileItem.url)) ||
      (isPdfFileLike(fileItem) && !forceSource && Boolean(fileItem.url)) ||
      (!forceSource &&
        ["svg", "markdown", "video", "audio"].includes(
          resolveFilePreviewKind({ name: fileItem.name, mimeType: fileItem.type, url: fileItem.url })
        ));
    const isMedia =
      isImageFile ||
      isPdfFileLike(fileItem) ||
      isModel3DFileLike(fileItem) ||
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
    // 文档类（Markdown 等）走最后一个分支：对齐 ZCode
    // `previewPaneMarkdownContent.tsx:42,54` 的 `w-full h-full bg-background
    // overflow-auto` + `p-4` —— 内容直接铺在面板里，**不套卡片**。旧版的
    // `rounded-2xl border + shadow` 会在已经带边框的面板里再画一圈线，
    // 属于「卡里套卡」的另一种形态。
    const contentShellClass = isSpreadsheet
      ? "min-h-0 flex-1 overflow-hidden bg-white"
      : isSelfScrollingPreview
        ? "min-h-0 flex-1 overflow-hidden"
        : isMedia || isSourceCode
          ? "min-h-0 flex-1 overflow-auto bg-white"
          : "mx-auto min-h-0 w-full max-w-[720px] flex-1 overflow-auto bg-white p-6 sm:p-8";

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
  // 表格、PPTX、PDF 以及自带外壳的 SVG / Markdown / 音视频预览都需要撑满面板并自管滚动，
  // 不能套 h-full overflow-auto p-4。
  const detailKind = resolveFilePreviewKind({
    name: fileItem.name,
    mimeType: fileItem.type,
    url: fileItem.url,
  });
  const isImmersiveDetail =
    ["csv", "xlsx", "xls"].includes(detailExt) ||
    (isPptFileLike(fileItem) && !forceSource && Boolean(fileItem.url)) ||
    (isPdfFileLike(fileItem) && !forceSource && Boolean(fileItem.url)) ||
    (!forceSource && ["svg", "markdown", "video", "audio"].includes(detailKind));

  return (
    <ActionViewFrame
      className={isImmersiveDetail ? "flex min-h-0 flex-col overflow-hidden bg-white/50" : "bg-white/50"}
      titleNode={
        <div className="flex min-w-0 items-center gap-2">
          {getFileIcon(fileItem.type)}
          <span className="truncate">{fileItem.name}</span>
        </div>
      }
      onClickTitle={clearActive}
    >
      <div className={isImmersiveDetail ? "flex h-full min-h-0 flex-col" : undefined}>
        <TooltipProvider>
          <div className="flex shrink-0 items-center justify-end gap-1 px-4 py-2">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 text-foreground-subtle hover:text-foreground"
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
                    className="h-8 w-8 text-foreground-subtle hover:text-foreground"
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

        <Separator className="shrink-0 bg-surface-hover" />

        <div
          className={
            isImmersiveDetail
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

FileList.displayName = "FileList";

export default FileList;
