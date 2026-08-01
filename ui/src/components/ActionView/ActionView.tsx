import React, {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";
import classNames from "classnames";
import { motion } from "motion/react";
import {
  Download,
  ExternalLink,
  Link2,
  Maximize2,
  Minimize2,
  RefreshCw,
  Star,
  X,
} from "lucide-react";
import { useMemoizedFn } from "ahooks";
import FilePreview from "./FilePreview";
import FileList from "./FileList";
import { PlanView, PlanViewAction } from "../PlanView";
import { PanelItemType } from "../ActionPanel";
import { ActionViewItemEnum, copyText, downloadFile, showMessage } from "@/utils";
import { iconType } from "@/utils/constants";
import {
  collectWorkspaceFiles,
  workspaceFileKey,
  type WorkspaceFileItem,
} from "./workspaceFiles";

const iconBtnClass =
  "flex h-7 w-7 items-center justify-center rounded-full text-[#86868b] transition-colors hover:bg-black/[0.05] hover:text-[#1d1d1f]";

type ActionViewRef = PlanViewAction & {
  setFilePreview: (file?: CHAT.TFile) => void;
  changeActionView: (item: ActionViewItemEnum) => void;
};

const useActionView = () => {
  const ref = useRef<ActionViewRef>(null);
  return ref;
};

type ActionViewProps = {
  title?: React.ReactNode;
  taskList?: PanelItemType[];
  activeTask?: CHAT.Task;
  streamTask?: CHAT.Task;
  /** 父级待打开文件（ActionView 未挂载时 changeFile 会先写入这里） */
  pendingPreviewFile?: CHAT.TFile;
  onPendingPreviewFileConsumed?: () => void;
  workspaceCaption?: string;
  plan?: CHAT.Plan;
  runState?: {
    status?: string;
    errorMsg?: string;
    finishedAt?: string;
  };
  isFocusMode?: boolean;
  onToggleFocusMode?: () => void;
  onClose?: () => void;
  ref?: React.Ref<ActionViewRef>;
};

type FileKind = "img" | "xlsx" | "md" | "html" | "pdf" | "css" | "code" | "py" | "file";

function resolveKind(type?: string, name?: string): FileKind {
  const ext = (type || name?.split(".").pop() || "").toLowerCase();
  if (["png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "avif"].includes(ext)) return "img";
  if (["csv", "xlsx", "xls"].includes(ext)) return "xlsx";
  if (["md", "markdown", "txt"].includes(ext)) return "md";
  if (["html", "htm"].includes(ext)) return "html";
  if (ext === "pdf") return "pdf";
  if (["css", "scss", "less"].includes(ext)) return "css";
  if (ext === "py") return "py";
  if (["js", "ts", "tsx", "jsx", "java", "json", "xml", "code"].includes(ext)) return "code";
  return "file";
}

function fileTypeLabel(type?: string, name?: string) {
  const kind = resolveKind(type, name);
  const ext = (type || name?.split(".").pop() || "").toUpperCase();
  switch (kind) {
    case "img":
      return `Image · ${ext || "PNG"}`;
    case "xlsx":
      return `Spreadsheet · ${ext || "XLSX"}`;
    case "md":
      return name?.replace(/\.[^.]+$/, "") || `Document · ${ext || "MD"}`;
    case "html":
      return "Web page · HTML";
    case "pdf":
      return "PDF · PDF";
    case "css":
      return "样式文件 · CSS";
    case "py":
      return "Python · PY";
    case "code":
      return `Code · ${ext || "FILE"}`;
    default:
      return ext ? `File · ${ext}` : "File";
  }
}

function kindBadge(kind: FileKind) {
  if (kind === "xlsx") return "X";
  if (kind === "py") return "Py";
  if (kind === "html" || kind === "code") return "</>";
  if (kind === "img") return "▢";
  if (kind === "pdf") return "P";
  return "T";
}

function kindTone(kind: FileKind) {
  switch (kind) {
    case "img":
    case "xlsx":
    case "md":
    case "html":
    case "code":
    case "py":
    case "pdf":
    case "css":
    default:
      return "bg-[#f5f5f7] text-[#6b6b70]";
  }
}

const ActionViewComp: ReactorType.FC<ActionViewProps> = forwardRef((props, ref) => {
  const {
    className,
    onClose,
    activeTask,
    streamTask,
    taskList,
    plan,
    runState,
    isFocusMode,
    onToggleFocusMode,
    pendingPreviewFile,
    onPendingPreviewFileConsumed,
  } = props;

  const planRef = useRef<PlanViewAction>(null);
  const tabsRef = useRef<HTMLDivElement>(null);
  const [openTabs, setOpenTabs] = useState<WorkspaceFileItem[]>([]);
  const [selectedKey, setSelectedKey] = useState<string>("");
  const [refreshToken, setRefreshToken] = useState(0);
  const [viewMode, setViewMode] = useState<"preview" | "source">("preview");
  /** follow=工具动态预览；file=文件 tab 预览。点工具切 follow，点文件切 file */
  const [panelMode, setPanelMode] = useState<"follow" | "file">("follow");

  // 全量产物仅作数据源；tab 只展示用户点开过的文件
  const catalog = useMemo(() => collectWorkspaceFiles(taskList), [taskList]);
  const catalogMap = useMemo(() => {
    const map = new Map<string, WorkspaceFileItem>();
    catalog.forEach((file) => {
      const key = workspaceFileKey(file);
      if (key) map.set(key, file);
    });
    return map;
  }, [catalog]);

  const files = useMemo(() => {
    return openTabs.map((tab) => {
      const key = workspaceFileKey(tab);
      const catalogHit = catalogMap.get(key);
      if (!catalogHit) return tab;
      // catalog 只补元数据，URL 以 tab 为准，避免同 key 覆盖错文件
      return {
        ...catalogHit,
        ...tab,
        url: tab.url || catalogHit.url,
        downloadUrl: tab.downloadUrl || tab.url || catalogHit.downloadUrl,
        name: tab.name || catalogHit.name,
        resourceKey: tab.resourceKey || catalogHit.resourceKey,
        type: tab.type || catalogHit.type,
        mimeType: tab.mimeType ?? catalogHit.mimeType,
        task: tab.task || catalogHit.task,
      };
    });
  }, [openTabs, catalogMap]);

  const openFileInTab = useMemoizedFn((file: CHAT.TFile) => {
    const key = workspaceFileKey(file);
    if (!key) return;

    const catalogHit = catalogMap.get(key);
    // 以用户点击的文件为准，catalog 仅补充 task 等元数据
    const resolved: WorkspaceFileItem = {
      name: file.name || catalogHit?.name || "未命名文件",
      url: file.url || catalogHit?.url || "",
      type: file.type || catalogHit?.type || "",
      size: file.size ?? catalogHit?.size ?? 0,
      downloadUrl: file.downloadUrl || file.url || catalogHit?.downloadUrl,
      missing: file.missing ?? catalogHit?.missing,
      missingReason: file.missingReason || catalogHit?.missingReason,
      resourceKey: file.resourceKey || catalogHit?.resourceKey,
      mimeType: file.mimeType ?? catalogHit?.mimeType,
      messageTime: catalogHit?.messageTime,
      task:
        catalogHit?.task ||
        (file as WorkspaceFileItem).task ||
        ({ messageType: "file" } as PanelItemType),
    };

    setOpenTabs((prev) => {
      const nextKey = workspaceFileKey(resolved);
      const existing = prev.findIndex(
        (item) => workspaceFileKey(item) === nextKey
      );
      if (existing >= 0) {
        const copy = [...prev];
        copy[existing] = resolved;
        return copy;
      }
      return [...prev, resolved];
    });
    setSelectedKey(workspaceFileKey(resolved));
    setViewMode("preview");
    setPanelMode("file");
  });

  // 父级 pending 文件：ActionView 刚挂载/展开时补开 tab
  useEffect(() => {
    if (!pendingPreviewFile) return;
    openFileInTab(pendingPreviewFile);
    onPendingPreviewFileConsumed?.();
  }, [pendingPreviewFile, openFileInTab, onPendingPreviewFileConsumed]);

  // 会话切换 / 产物清空时重置已打开 tab
  useEffect(() => {
    if (!taskList?.length && !catalog.length) {
      setOpenTabs([]);
      setSelectedKey("");
      setPanelMode("follow");
    }
  }, [taskList, catalog.length]);

  useEffect(() => {
    setViewMode("preview");
  }, [selectedKey]);

  useImperativeHandle(ref, () => {
    return {
      ...planRef.current!,
      setFilePreview: (file) => {
        if (!file) {
          return;
        }
        openFileInTab(file);
      },
      changeActionView: (item) => {
        if (item === ActionViewItemEnum.follow) {
          setPanelMode("follow");
          return;
        }
        if (item === ActionViewItemEnum.file || item === ActionViewItemEnum.browser) {
          setPanelMode("file");
        }
      },
    };
  });

  const selectedFile: WorkspaceFileItem | undefined = useMemo(() => {
    if (!files.length) return undefined;
    if (!selectedKey) return files[files.length - 1];
    return files.find((f) => workspaceFileKey(f) === selectedKey) || files[files.length - 1];
  }, [files, selectedKey]);

  const showFileBrowser = panelMode === "file" && files.length > 0;
  const downloadUrl =
    selectedFile?.downloadUrl || selectedFile?.url || "";
  const selectedExt = (
    selectedFile?.type ||
    selectedFile?.name?.split(".").pop() ||
    ""
  ).toLowerCase();
  // HTML 默认直接渲染；源码模式仍可看源码
  const canSourceMode = Boolean(
    selectedFile &&
      [
        "md",
        "markdown",
        "txt",
        "json",
        "js",
        "ts",
        "tsx",
        "jsx",
        "py",
        "java",
        "xml",
        "html",
        "htm",
        "css",
        "yml",
        "yaml",
        "sql",
        "sh",
        "log",
        "csv",
        "code",
      ].includes(selectedExt)
  );

  const handleDownload = useMemoizedFn(() => {
    if (!downloadUrl || !selectedFile) return;
    downloadFile(downloadUrl, selectedFile.name);
  });

  const handleOpenExternal = useMemoizedFn(() => {
    if (!downloadUrl) return;
    window.open(downloadUrl, "_blank", "noopener,noreferrer");
  });

  const handleCopyLink = useMemoizedFn(() => {
    if (!downloadUrl) {
      showMessage()?.warning("暂无可复制链接");
      return;
    }
    copyText(downloadUrl);
    showMessage()?.success("链接已复制");
  });

  const selectedKind = resolveKind(selectedFile?.type, selectedFile?.name);
  // PDF 对齐浏览器式预览：保留完整文件名（含扩展名）
  const metaTitle =
    selectedKind === "pdf"
      ? selectedFile?.name || ""
      : selectedFile?.name?.replace(/\.[^.]+$/, "") || selectedFile?.name || "";
  const metaSub = selectedFile
    ? fileTypeLabel(selectedFile.type, selectedFile.name)
    : "";
  const selectedPdfIcon =
    selectedKind === "pdf" ? iconType.pdf : undefined;

  return (
    <motion.div
      className={classNames(
        "flex h-full w-full flex-col overflow-hidden rounded-[20px] bg-[#f3f3f5]",
        className
      )}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{
        duration: 0.35,
        ease: [0.25, 0.46, 0.45, 0.94],
      }}
    >
      {/* 顶栏：「动态」+ 已打开文件 pill；点工具回动态，点文件进文件预览 */}
      <div className="flex shrink-0 flex-col gap-1 px-3 pt-2.5 pb-1.5">
        <div className="flex items-center gap-2">
          {files.length > 0 ? (
            <div
              ref={tabsRef}
              className="flex min-w-0 flex-1 items-center gap-1.5 overflow-x-auto pb-0.5 [scrollbar-color:#c7c7cc_transparent] [scrollbar-width:thin] [&::-webkit-scrollbar]:h-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-[#c7c7cc]"
            >
              <button
                type="button"
                onClick={() => setPanelMode("follow")}
                className={classNames(
                  "inline-flex h-8 shrink-0 items-center rounded-full px-2.5 text-[12.5px] font-medium transition-colors",
                  panelMode === "follow"
                    ? "bg-white text-[#1d1d1f] shadow-[0_1px_2px_rgba(0,0,0,0.04)] ring-1 ring-black/[0.04]"
                    : "bg-transparent text-[#86868b] hover:bg-white/70 hover:text-[#1d1d1f]"
                )}
              >
                动态
              </button>
              {files.map((file) => {
                const key = workspaceFileKey(file);
                const active =
                  panelMode === "file" && key === workspaceFileKey(selectedFile);
                const kind = resolveKind(file.type, file.name);
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => {
                      setSelectedKey(key);
                      setPanelMode("file");
                    }}
                    className={classNames(
                      "inline-flex h-8 max-w-[200px] shrink-0 items-center gap-1.5 rounded-full px-2.5 text-left transition-colors",
                      active
                        ? "bg-white text-[#1d1d1f] shadow-[0_1px_2px_rgba(0,0,0,0.04)] ring-1 ring-black/[0.04]"
                        : "bg-transparent text-[#86868b] hover:bg-white/70 hover:text-[#1d1d1f]"
                    )}
                    title={file.name}
                  >
                    <span
                      className={classNames(
                        "flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-md text-[10px] font-bold leading-none",
                        kindTone(kind)
                      )}
                    >
                      {kindBadge(kind)}
                    </span>
                    <span className="truncate text-[12.5px] font-medium">
                      {file.name}
                    </span>
                  </button>
                );
              })}
            </div>
          ) : (
            <div className="min-w-0 flex-1 truncate px-1 text-[13px] font-medium text-[#86868b]">
              动态
            </div>
          )}

          <div className="ml-auto flex shrink-0 items-center gap-0.5">
            {onToggleFocusMode ? (
              <button
                type="button"
                onClick={onToggleFocusMode}
                className={classNames(
                  iconBtnClass,
                  isFocusMode && "bg-white text-[#1d1d1f] shadow-sm"
                )}
                title={isFocusMode ? "退出沉浸模式" : "进入沉浸模式"}
              >
                {isFocusMode ? (
                  <Minimize2 className="h-3.5 w-3.5" />
                ) : (
                  <Maximize2 className="h-3.5 w-3.5" />
                )}
              </button>
            ) : null}
            {downloadUrl ? (
              <button
                type="button"
                className={iconBtnClass}
                title="在新窗口打开"
                onClick={handleOpenExternal}
              >
                <ExternalLink className="h-3.5 w-3.5" />
              </button>
            ) : null}
            <button
              type="button"
              onClick={onClose}
              className={iconBtnClass}
              title="关闭文件"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* 白卡片：工具条 + 预览 */}
      <div className="mx-2 mb-2 flex min-h-0 flex-1 flex-col overflow-hidden rounded-[16px] border border-black/[0.04] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.03)]">
        {showFileBrowser && selectedFile ? (
          <>
            <div className="flex shrink-0 items-center gap-2.5 border-b border-[#f0f0f2] px-3 py-2.5">
              {canSourceMode ? (
                <div className="inline-flex shrink-0 rounded-full bg-[#f3f3f5] p-0.5">
                  <button
                    type="button"
                    className={classNames(
                      "h-7 rounded-full px-3 text-[12.5px] font-medium transition-colors",
                      viewMode === "preview"
                        ? "bg-white text-[#1d1d1f] shadow-[0_0_0_1px_rgba(0,0,0,0.04),0_1px_2px_rgba(0,0,0,0.04)]"
                        : "text-[#86868b]"
                    )}
                    onClick={() => setViewMode("preview")}
                  >
                    预览
                  </button>
                  <button
                    type="button"
                    className={classNames(
                      "h-7 rounded-full px-3 text-[12.5px] font-medium transition-colors",
                      viewMode === "source"
                        ? "bg-white text-[#1d1d1f] shadow-[0_0_0_1px_rgba(0,0,0,0.04),0_1px_2px_rgba(0,0,0,0.04)]"
                        : "text-[#86868b]"
                    )}
                    onClick={() => setViewMode("source")}
                  >
                    源码
                  </button>
                </div>
              ) : null}

              <div className="flex min-w-0 flex-1 items-center gap-2.5">
                {selectedPdfIcon ? (
                  <img
                    src={selectedPdfIcon}
                    alt=""
                    className="h-8 w-8 shrink-0 rounded-md object-contain"
                  />
                ) : (
                  <span
                    className={classNames(
                      "flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-[12px] font-bold",
                      kindTone(selectedKind)
                    )}
                  >
                    {kindBadge(selectedKind)}
                  </span>
                )}
                <div className="min-w-0">
                  <div className="truncate text-[13.5px] font-semibold tracking-[-0.01em] leading-tight text-[#1d1d1f]">
                    {metaTitle}
                  </div>
                  <div className="mt-px truncate text-[12px] leading-snug text-[#86868b]">
                    {metaSub}
                  </div>
                </div>
              </div>

              <div className="flex shrink-0 items-center gap-0.5">
                {selectedKind !== "pdf" ? (
                  <button
                    type="button"
                    className={iconBtnClass}
                    title="复制链接"
                    onClick={handleCopyLink}
                  >
                    <Link2 className="h-3.5 w-3.5" />
                  </button>
                ) : null}
                <button
                  type="button"
                  className={iconBtnClass}
                  title="刷新"
                  onClick={() => setRefreshToken((n) => n + 1)}
                >
                  <RefreshCw className="h-3.5 w-3.5" />
                </button>
                <button
                  type="button"
                  className={iconBtnClass}
                  title="下载"
                  disabled={!downloadUrl}
                  onClick={handleDownload}
                >
                  <Download className="h-3.5 w-3.5" />
                </button>
                {selectedKind === "pdf" ? (
                  <button
                    type="button"
                    className={iconBtnClass}
                    title="收藏"
                  >
                    <Star className="h-3.5 w-3.5" />
                  </button>
                ) : null}
              </div>
            </div>

            <div
              className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white"
              key={`${workspaceFileKey(selectedFile)}-${refreshToken}-${viewMode}`}
            >
              {viewMode === "source" && canSourceMode ? (
                <div className="min-h-0 flex-1 overflow-auto">
                  <FileList
                    taskList={taskList}
                    activeFile={selectedFile}
                    embedded
                    forceSource
                  />
                </div>
              ) : (
                <div className="min-h-0 flex-1 overflow-hidden">
                  <FileList
                    taskList={taskList}
                    activeFile={selectedFile}
                    embedded
                  />
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="min-h-0 flex-1 overflow-hidden">
            <FilePreview
              taskItem={activeTask || streamTask}
              taskList={taskList}
              runState={runState}
              className="h-full"
            />
          </div>
        )}
      </div>

      <PlanView plan={plan} ref={planRef} />
    </motion.div>
  );
});

// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-expect-error
const ActionView: typeof ActionViewComp & {
  useActionView: typeof useActionView;
} = ActionViewComp;
ActionView.useActionView = useActionView;

export default ActionView;
