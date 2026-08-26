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
import { DURATION, EASE_OUT, useMotionConfig } from "@/lib/motion";
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
import {
  FILE_KIND_TONE,
  fileKindBadge,
  fileKindLabel,
  resolveFileKind,
} from "@/utils/fileKind";
import { isTextCopyableFileLike } from "@/utils/taskArtifacts";

const iconBtnClass =
  "reactor-action-icon flex h-7 w-7 items-center justify-center rounded-full text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-hover)] hover:text-[var(--color-text)]";

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

  // 全量产物仅作数据源；tab 只展示用户点开过的文件。动态流持续增加文件时
  // 不强行改变用户当前预览，用户选择才是 openTabs 的生命周期入口。
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
    // tab 优先保留用户刚点击时的 URL/文件信息，catalog 只补充任务等元数据；
    // 这是应对流式产物后续补全、但同一资源 key 已在预览中的合并边界。
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
    // 打开文件是幂等操作：按稳定资源 key 更新已有 tab，否则追加新 tab；同时
    // 切到 file/preview 模式，让父级 pending 文件和用户点击走同一条路径。
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

  // ChatView 可能先收到文件点击、后挂载 ActionView，因此 pending 文件必须在
  // 子视图挂载后消费一次，消费完成由父级清空，避免重复打开。
  useEffect(() => {
    if (!pendingPreviewFile) return;
    openFileInTab(pendingPreviewFile);
    onPendingPreviewFileConsumed?.();
  }, [pendingPreviewFile, openFileInTab, onPendingPreviewFileConsumed]);

  // 没有任务产物时认为会话工作区已清空，关闭旧 tab；有产物时保留用户已打开
  // 的 tab，避免每次流式 taskList 更新都重置预览位置。
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
    // 暴露给 ChatView 的命令只改变 ActionView 自己的视图状态，不把文件目录
    // 逻辑泄漏到父组件；follow/file 两种模式对应动态流和用户文件预览边界。
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
    // selectedKey 失效时回退到最新 tab，保证目录更新或删除后仍有稳定的可视内容。
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
      (isTextCopyableFileLike(selectedFile) || selectedExt === "code")
  );
  const forceSource = viewMode === "source" && canSourceMode;

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

  const selectedKind = resolveFileKind(selectedFile?.type, selectedFile?.name);
  // PDF 对齐浏览器式预览：保留完整文件名（含扩展名）
  const metaTitle =
    selectedKind === "pdf"
      ? selectedFile?.name || ""
      : selectedFile?.name?.replace(/\.[^.]+$/, "") || selectedFile?.name || "";
  const metaSub = selectedFile
    ? fileKindLabel(selectedFile.type, selectedFile.name)
    : "";
  const selectedPdfIcon =
    selectedKind === "pdf" ? iconType.pdf : undefined;

  const { reduce } = useMotionConfig();

  return (
    <motion.div
      className={classNames(
        "reactor-workspace-content flex h-full w-full flex-col overflow-hidden bg-[var(--color-bg)]",
        className
      )}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{
        duration: reduce ? DURATION.reduced : 0.22,
        ease: EASE_OUT,
      }}
    >
      {/* 顶栏：「动态」+ 已打开文件 pill；点工具回动态，点文件进文件预览 */}
      <div className="reactor-workspace-toolbar flex shrink-0 flex-col gap-1 border-b border-[var(--color-line)] px-3 pt-2.5 pb-1.5">
        <div className="flex items-center gap-2">
          {files.length > 0 ? (
            <div
              ref={tabsRef}
              className="reactor-workspace-tabs flex min-w-0 flex-1 items-center gap-1.5 overflow-x-auto pb-0.5 [scrollbar-color:#c7c7cc_transparent] [scrollbar-width:thin] [&::-webkit-scrollbar]:h-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-[#c7c7cc]"
            >
              <button
                type="button"
                onClick={() => setPanelMode("follow")}
                className={classNames(
                  "inline-flex h-8 shrink-0 items-center rounded-[6px] px-2.5 text-[12.5px] font-medium transition-colors",
                  panelMode === "follow"
                    ? "bg-[var(--color-hover)] text-[var(--color-text)]"
                    : "bg-transparent text-[var(--color-text-muted)] hover:bg-[var(--color-surface-raised)] hover:text-[var(--color-text)]"
                )}
              >
                动态
              </button>
              {files.map((file) => {
                const key = workspaceFileKey(file);
                const active =
                  panelMode === "file" && key === workspaceFileKey(selectedFile);
                const kind = resolveFileKind(file.type, file.name);
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => {
                      setSelectedKey(key);
                      setPanelMode("file");
                    }}
                    className={classNames(
                      "inline-flex h-8 max-w-[200px] shrink-0 items-center gap-1.5 rounded-[6px] px-2.5 text-left transition-colors",
                      active
                        ? "bg-[var(--color-hover)] text-[var(--color-text)]"
                        : "bg-transparent text-[var(--color-text-muted)] hover:bg-[var(--color-surface-raised)] hover:text-[var(--color-text)]"
                    )}
                    title={file.name}
                  >
                    <span
                      className={classNames(
                        "flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-md text-[10px] font-bold leading-none",
                        FILE_KIND_TONE
                      )}
                    >
                      {fileKindBadge(kind)}
                    </span>
                    <span className="truncate text-[12.5px] font-medium">
                      {file.name}
                    </span>
                  </button>
                );
              })}
            </div>
          ) : (
            <div className="min-w-0 flex-1 truncate px-1 text-[13px] font-medium text-[var(--color-text-muted)]">
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
                  isFocusMode && "bg-[var(--color-surface-raised)] text-[var(--color-text)] shadow-[var(--shadow-xs)]"
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

      {/* 工作区内容：工具条 + 预览 */}
      <div className="mx-2 mb-2 flex min-h-0 flex-1 flex-col overflow-hidden bg-[var(--color-surface-raised)]">
        {showFileBrowser && selectedFile ? (
          <>
            <div className="flex shrink-0 items-center gap-2.5 border-b border-[var(--color-line)] px-3 py-2.5">
              {canSourceMode ? (
                <div className="inline-flex shrink-0 rounded-full bg-[var(--color-surface-sunken)] p-0.5">
                  <button
                    type="button"
                    className={classNames(
                      "h-7 rounded-full px-3 text-[12.5px] font-medium transition-colors",
                      viewMode === "preview"
                        ? "bg-[var(--color-surface-raised)] text-[var(--color-text)] shadow-[var(--shadow-xs)]"
                        : "text-[var(--color-text-muted)]"
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
                        ? "bg-[var(--color-surface-raised)] text-[var(--color-text)] shadow-[var(--shadow-xs)]"
                        : "text-[var(--color-text-muted)]"
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
                      FILE_KIND_TONE
                    )}
                  >
                    {fileKindBadge(selectedKind)}
                  </span>
                )}
                <div className="min-w-0">
                  <div className="truncate text-[13.5px] font-semibold tracking-[-0.01em] leading-tight text-[var(--color-text)]">
                    {metaTitle}
                  </div>
                  <div className="mt-px truncate text-[12px] leading-snug text-[var(--color-text-muted)]">
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
              className="tab-content-enter flex min-h-0 flex-1 flex-col overflow-hidden bg-[var(--color-surface-raised)]"
              key={`${workspaceFileKey(selectedFile)}-${refreshToken}-${viewMode}`}
            >
              <div
                className={
                  forceSource
                    ? "min-h-0 flex-1 overflow-auto"
                    : "min-h-0 flex-1 overflow-hidden"
                }
              >
                <FileList
                  taskList={taskList}
                  activeFile={selectedFile}
                  embedded
                  forceSource={forceSource}
                />
              </div>
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
