import React, {
  Fragment,
  forwardRef,
  memo,
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
  ChevronRightIcon,
  Download,
  ExternalLink,
  ArrowLeft,
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
  findWorkspaceFileByPath,
  isSourceTab,
  resolveWorkspacePaneMode,
  workspaceFileKey,
  workspaceTabKey,
  type WorkspaceFileItem,
  type WorkspaceSourceTab,
  type WorkspaceTab,
} from "./workspaceFiles";
import {
  pathBreadcrumb,
  sourceTabKey,
  sourceTabMeta,
  type CodeViewerSource,
} from "@/lib/codeViewerSource";
import { ToolPreviewPanel } from "../Dialogue/ToolPreviewPanel";
import {
  resolveFilePreviewKind,
  shouldShowPreviewHeaderDivider,
} from "@/lib/filePreview/kinds";
import {
  FILE_KIND_TONE,
  fileKindBadge,
  resolveFileKind,
} from "@/utils/fileKind";
import { isTextCopyableFileLike } from "@/utils/taskArtifacts";

// ZCode PreviewPane.tsx:1596-1605 —— 头部操作一律 ghost 图标按钮：rounded-lg、
// 三级前景、hover 才升到主前景；不用实心底色，保持头部安静。
const iconBtnClass =
  "reactor-action-icon flex h-7 w-7 items-center justify-center rounded-lg text-foreground-subtle transition-colors hover:bg-surface-hover hover:text-foreground";

type ActionViewRef = PlanViewAction & {
  setFilePreview: (file?: CHAT.TFile) => void;
  changeActionView: (item: ActionViewItemEnum) => void;
  /**
   * 打开一个 `CodeViewerSource` tab（Stage 4）。
   * 会话产物文件仍走 `setFilePreview`；patch / 内联文本 / 无 URL 的 file 走这里。
   */
  openSourceTab: (tab: {
    source: CodeViewerSource;
    fileUrl?: string;
    unavailableReason?: string;
    output?: string[];
    status?: "running" | "ok" | "error";
  }) => void;
};

const useActionView = () => {
  const ref = useRef<ActionViewRef>(null);
  return ref;
};

type ActionViewProps = {
  title?: React.ReactNode;
  className?: string;
  taskList?: PanelItemType[];
  activeTask?: CHAT.Task;
  streamTask?: CHAT.Task;
  /** 父级待打开文件（ActionView 未挂载时 changeFile 会先写入这里） */
  pendingPreviewFile?: CHAT.TFile;
  onPendingPreviewFileConsumed?: () => void;
  /** 父级待打开的 source tab（工具卡 chip 推来，ActionView 未挂载时先暂存） */
  pendingSourceTab?: {
    source: CodeViewerSource;
    fileUrl?: string;
    unavailableReason?: string;
    output?: string[];
    status?: "running" | "ok" | "error";
  } | null;
  onPendingSourceTabConsumed?: () => void;
  workspaceCaption?: string;
  plan?: CHAT.Plan;
  isFocusMode?: boolean;
  onToggleFocusMode?: () => void;
  onBack?: () => void;
  onClose?: () => void;
  ref?: React.Ref<ActionViewRef>;
};

const ActionViewInner = forwardRef<ActionViewRef, ActionViewProps>((props, ref) => {
  const {
    className,
    onClose,
    activeTask,
    streamTask,
    taskList,
    plan,
    isFocusMode,
    onToggleFocusMode,
    onBack,
    pendingPreviewFile,
    onPendingPreviewFileConsumed,
    pendingSourceTab,
    onPendingSourceTabConsumed,
  } = props;

  const planRef = useRef<PlanViewAction>(null);
  const tabsRef = useRef<HTMLDivElement>(null);
  const [openTabs, setOpenTabs] = useState<WorkspaceTab[]>([]);
  const [selectedKey, setSelectedKey] = useState<string>("");
  const [refreshToken, setRefreshToken] = useState(0);
  const [viewMode, setViewMode] = useState<"preview" | "source">("preview");
  /** follow=工具动态预览；file=文件 tab 预览。点工具切 follow，点文件切 file */
  const [panelMode, setPanelMode] = useState<"follow" | "file">("follow");
  /**
   * tab 条左右是否还有可滚动内容 —— 决定边缘渐隐遮罩挂哪一侧。
   * 对齐 ZCode `AnimatedSidePanePanel.tsx:968-984` 的 `tabsScrollMaskEdges`。
   */
  const [tabsMask, setTabsMask] = useState({
    left: false,
    right: false,
  });

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
    // source tab 不参与 catalog 合并（它没有产物身份），原样透传。
    return openTabs.map((tab) => {
      if (isSourceTab(tab)) return tab;
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

  /**
   * 打开一个 `CodeViewerSource` tab（Stage 4）。
   *
   * 与 `openFileInTab` 平行：source tab 没有产物 URL，`workspaceFileKey` 会返回空串
   * 导致早退，因此必须走独立分支。幂等键用 `sourceTabKey`（patch 带内容哈希）。
   */
  const openSourceInTab = useMemoizedFn((tab: Omit<WorkspaceSourceTab, "kind" | "key">) => {
    const key = sourceTabKey(tab.source);
    setOpenTabs((prev) => {
      const existing = prev.findIndex((item) => workspaceTabKey(item) === key);
      const next: WorkspaceSourceTab = {
        ...tab,
        kind: "source",
        key
      };
      if (existing >= 0) {
        const copy = [...prev];
        copy[existing] = next;
        return copy;
      }
      return [...prev, next];
    });
    setSelectedKey(key);
    setViewMode("preview");
    setPanelMode("file");
  });

  const closeTab = useMemoizedFn((key: string) => {
    setOpenTabs((prev) => {
      const idx = prev.findIndex((item) => workspaceTabKey(item) === key);
      if (idx < 0) return prev;
      const next = prev.filter((item) => workspaceTabKey(item) !== key);
      // 关掉当前选中项时，把选区挪到右邻（没有则左邻），保持面板不空白。
      setSelectedKey((current) => {
        if (current !== key) return current;
        const fallback = next[idx] || next[idx - 1];
        return fallback ? workspaceTabKey(fallback) : "";
      });
      return next;
    });
  });

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
      previewUrl: file.previewUrl || file.url || catalogHit?.previewUrl || catalogHit?.url,
      downloadUrl: file.downloadUrl || file.url || catalogHit?.downloadUrl,
      missing: file.missing ?? catalogHit?.missing,
      missingReason: file.missingReason || catalogHit?.missingReason,
      resourceKey: file.resourceKey || catalogHit?.resourceKey,
      mimeType: file.mimeType ?? catalogHit?.mimeType,
      originFileName: file.originFileName || catalogHit?.originFileName,
      relativePath: file.relativePath || catalogHit?.relativePath,
      messageTime: catalogHit?.messageTime,
      task:
        catalogHit?.task ||
        (file as WorkspaceFileItem).task ||
        ({ messageType: "file" } as PanelItemType),
    };

    setOpenTabs((prev) => {
      const nextKey = workspaceFileKey(resolved);
      const existing = prev.findIndex(
        (item) => workspaceTabKey(item) === nextKey
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

  // 工具卡 chip 推来的 source tab。同样需要「挂载后消费一次」。
  // 特例：`file` source 若命中会话产物 catalog，就并进同 key 的文件 tab ——
  // 那条链路有完整的文件工具条（下载 / 外开 / 源码切换），比 source tab 更完整。
  // 命中不了则显式带上降级原因，让面板同步渲染提示而不是先闪一个 spinner
  // （TASKS.md §5 决策点 B：前端没有按绝对路径读文件的接口）。
  useEffect(() => {
    if (!pendingSourceTab) return;
    const { source, ...rest } = pendingSourceTab;
    if (source.type === "file") {
      const hit = findWorkspaceFileByPath(catalog, source.path);
      if (hit) {
        openFileInTab(hit as CHAT.TFile);
      } else {
        openSourceInTab({
          ...rest,
          source,
          unavailableReason:
            rest.unavailableReason || `该文件不在会话产物中，无法预览：${source.path}`,
        });
      }
    } else {
      openSourceInTab({
        ...rest,
        source
      });
    }
    onPendingSourceTabConsumed?.();
  }, [pendingSourceTab, catalog, openFileInTab, openSourceInTab, onPendingSourceTabConsumed]);

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

  /**
   * tab 条溢出探测：滚动 / 容器尺寸变化时更新左右遮罩。
   *
   * 对齐 ZCode `AnimatedSidePanePanel.tsx:968-984` —— 只在真的有溢出的一侧做
   * `mask-image` 渐隐，所以滚动条可以隐藏（遮罩本身就是「还能滚」的提示）。
   * `files.length` 进依赖是因为容器只在有 tab 时才挂载。
   */
  useEffect(() => {
    const el = tabsRef.current;
    if (!el) return;
    const update = () => {
      const left = el.scrollLeft > 1;
      const right = el.scrollLeft + el.clientWidth < el.scrollWidth - 1;
      setTabsMask((prev) => {
        if (prev.left === left && prev.right === right) {
          return prev;
        }
        return {
          left,
          right,
        };
      });
    };
    update();
    el.addEventListener("scroll", update, { passive: true });
    const observer = new ResizeObserver(update);
    observer.observe(el);
    return () => {
      el.removeEventListener("scroll", update);
      observer.disconnect();
    };
  }, [files.length]);

  /**
   * 激活的 tab 滚入视野。
   *
   * tab 采用 ZCode 的等宽收缩（156px → 60px）后，tab 多起来时新打开的 tab 可能
   * 直接落在视野外 —— ZCode 侧栏 tab 少、没遇到这个问题，这里必须补上。
   * 只做「刚好露出 + 8px 余量」，不做居中，避免每次点击都大幅跳动。
   */
  useEffect(() => {
    if (panelMode !== "file") return;
    const strip = tabsRef.current;
    if (!strip) return;
    const active = strip.querySelector<HTMLElement>('[data-active="true"]');
    if (!active) return;
    const stripRect = strip.getBoundingClientRect();
    const tabRect = active.getBoundingClientRect();
    if (tabRect.left < stripRect.left) {
      strip.scrollLeft -= stripRect.left - tabRect.left + 8;
    } else if (tabRect.right > stripRect.right) {
      strip.scrollLeft += tabRect.right - stripRect.right + 8;
    }
  }, [selectedKey, panelMode, files.length]);

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
      openSourceTab: (tab) => {
        // parent 的 chat 轮次尚未挂载时，tab 也得先落盘，因此复用 pending 通道：
        // 这里直接写 state（ActionView 已挂载才会被调用），无需父级缓存。
        openSourceInTab(tab);
      },
    };
  });

  const selectedTab: WorkspaceTab | undefined = useMemo(() => {
    // selectedKey 失效时回退到最新 tab，保证目录更新或删除后仍有稳定的可视内容。
    if (!files.length) return undefined;
    if (!selectedKey) return files[files.length - 1];
    return files.find((f) => workspaceTabKey(f) === selectedKey) || files[files.length - 1];
  }, [files, selectedKey]);

  // file tab 与 source tab 分流：前者走产物预览，后者走工具预览面板。
  const selectedFile: WorkspaceFileItem | undefined =
    selectedTab && !isSourceTab(selectedTab) ? selectedTab : undefined;
  const selectedSource: WorkspaceSourceTab | undefined =
    selectedTab && isSourceTab(selectedTab) ? selectedTab : undefined;

  const showFileBrowser = panelMode === "file" && files.length > 0;
  /** 内容区分派：follow（动态流）/ source（工具推来的 patch·text·image）/ file（产物文件）。 */
  const paneMode = resolveWorkspacePaneMode({
    panelMode,
    hasSelectedSource: Boolean(selectedSource),
    hasSelectedFile: Boolean(selectedFile),
  });
  const previewUrl =
    selectedFile?.previewUrl || selectedFile?.url || "";
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
    if (!previewUrl) return;
    window.open(previewUrl, "_blank", "noopener,noreferrer");
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

  /**
   * 单行面包屑（对齐 ZCode `PreviewPane.tsx:1546-1568`）。
   *
   * 旧版这里是「badge + 两行文本」：title 去掉扩展名、subtitle 是类型标签
   * （如 `Python · PY`）。两行加起来的信息量不抵一行完整文件名，且非 PDF 时
   * title 丢掉了扩展名，反而更难辨认。
   */
  const fileCrumb = useMemo(() => {
    const raw = selectedFile?.relativePath || selectedFile?.name || "";
    const parsed = pathBreadcrumb(raw);
    return {
      parents: parsed.parents,
      file: selectedFile?.name || parsed.file,
    };
  }, [selectedFile]);

  // 头部底部分隔线条件渲染：只有 pdf / Office / 音视频才画（见
  // shouldShowPreviewHeaderDivider）。代码 / 文本 / 图片的头部与内容同属一个平面。
  const showFileHeaderDivider = useMemo(
    () =>
      shouldShowPreviewHeaderDivider(
        resolveFilePreviewKind({
          name: selectedFile?.name,
          mimeType: selectedFile?.mimeType ?? undefined,
        })
      ),
    [selectedFile?.name, selectedFile?.mimeType]
  );

  const selectedPdfIcon =
    selectedKind === "pdf" ? iconType.pdf : undefined;

  const { reduce } = useMotionConfig();

  return (
    <motion.div
      className={classNames(
        "reactor-workspace-content flex h-full w-full flex-col overflow-hidden bg-background",
        className
      )}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{
        duration: reduce ? DURATION.reduced : 0.22,
        ease: EASE_OUT,
      }}
    >
      {/* 顶栏：「动态」+ 已打开文件 pill；点工具回动态，点文件进文件预览。
          ZCode `AnimatedSidePanePanel.tsx:946` 的 TabsList 是
          `border-b border-border/50 bg-transparent` —— **半透明分隔线 + 无底色**。
          全量 `border-border` 配 `bg-surface/30` 会把这一行画成一条明显的横杠。 */}
      <div className="reactor-workspace-toolbar flex h-10 shrink-0 items-center px-3">
        <div className="flex min-w-0 flex-1 items-center gap-2">
          {onBack ? (
            <button
              type="button"
              className={iconBtnClass}
              title="返回子 Agent"
              aria-label="返回子 Agent"
              onClick={onBack}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
            </button>
          ) : null}
          {files.length > 0 ? (
            <div
              ref={tabsRef}
              className={classNames(
                // 滚动条隐藏：边缘渐隐遮罩（mask-*，见 global.css）已经承担「还能滚」的
                // 提示，再挂一条细滚动条就是多一条线（ZCode 用 `!scrollbar-hide`）。
                "reactor-workspace-tabs flex min-w-0 flex-1 items-center gap-1 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
                tabsMask.left && tabsMask.right
                  ? "mask-both"
                  : tabsMask.left
                    ? "mask-left"
                    : tabsMask.right
                      ? "mask-right"
                      : ""
              )}
            >
              <button
                type="button"
                onClick={() => setPanelMode("follow")}
                className={classNames(
                  // ZCode 选中态走 bg-selected + text-foreground，未选中只有 hover 才上底色，
                  // 不再给每个 pill 挂 shadow-xs（DESIGN.md：阴影只留给菜单/浮层）。
                  "inline-flex h-7 shrink-0 items-center rounded-lg px-2 text-ui-sm font-medium transition-colors",
                  panelMode === "follow"
                    ? "bg-selected text-foreground"
                    : "text-foreground-subtle hover:bg-surface-hover hover:text-foreground"
                )}
              >
                动态
              </button>
              {files.length > 1 ? (
                <button
                  type="button"
                  onClick={() => {
                    setOpenTabs([]);
                    setSelectedKey("");
                    setPanelMode("follow");
                  }}
                  className="inline-flex h-7 shrink-0 items-center rounded-lg px-2 text-ui-sm text-foreground-subtle transition-colors hover:bg-surface-hover hover:text-foreground"
                  title="关闭全部标签页"
                >
                  全部关闭
                </button>
              ) : null}
              {files.map((tab) => {
                const isSource = isSourceTab(tab);
                const key = workspaceTabKey(tab);
                const active =
                  panelMode === "file" && key === workspaceTabKey(selectedTab || files[0]);
                const meta = isSource
                  ? sourceTabMeta(tab.source)
                  : {
                    title: (tab as WorkspaceFileItem).name,
                    kind: "file" as const
                  };
                const kind = isSource
                  ? resolveFileKind(meta.kind === "patch" ? "diff" : undefined, meta.title)
                  : resolveFileKind((tab as WorkspaceFileItem).type, (tab as WorkspaceFileItem).name);
                return (
                  // 分组容器：tab 主体与关闭按钮是两个动作，不能嵌套在同一个 button 里。
                  // 对齐 ZCode `SidePaneTabTrigger.tsx:147-162`：
                  // - `border border-transparent` 预留边框位，active 只换底色 → 不跳变
                  // - 基准宽 9.75rem（156px），**不增长**、可收缩 → tab 少时等宽整齐，
                  //   多时收缩后滚动；ZCode 的 `min-w-15`（60px）在 Reactor 的宽面板下
                  //   会把「徽章 + 标题 + 关闭按钮」挤到看不见标题，故下限抬到 110px。
                  // - 关闭按钮 `absolute right-1`，**不占 flex 流** → 标题宽度恒定
                  // - 中键关闭（ZCode 同款 auxclick 处理，避免浏览器自动滚动）
                  <span
                    key={key}
                    data-active={active ? "true" : undefined}
                    onAuxClick={(event) => {
                      if (event.button !== 1) return;
                      event.preventDefault();
                      event.stopPropagation();
                      closeTab(key);
                    }}
                    className={classNames(
                      "reactor-workspace-tab group relative flex h-7 max-w-[156px] min-w-[110px] flex-[0_1_9.75rem] items-center overflow-hidden rounded-lg border border-transparent pl-2 pr-1 transition-colors",
                      active
                        ? "bg-selected text-foreground"
                        : "text-foreground-subtle hover:bg-surface-hover hover:text-foreground"
                    )}
                  >
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedKey(key);
                        setPanelMode("file");
                      }}
                      className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
                      title={meta.title}
                    >
                      <span
                        className={classNames(
                          "flex size-4 shrink-0 items-center justify-center rounded-sm text-ui-xs font-bold leading-none",
                          FILE_KIND_TONE
                        )}
                      >
                        {fileKindBadge(kind)}
                      </span>
                      <span className="reactor-workspace-tab-title min-w-0 text-ui-sm font-medium">
                        {meta.title}
                      </span>
                    </button>
                    <button
                      type="button"
                      onPointerDown={(event) => event.stopPropagation()}
                      onClick={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        closeTab(key);
                      }}
                      className={classNames(
                        "absolute right-1 top-1/2 flex size-4 -translate-y-1/2 items-center justify-center rounded-sm text-foreground-subtle transition-opacity hover:bg-surface-hover hover:text-foreground",
                        // active 的 tab 常显关闭按钮；其余 hover / 键盘聚焦时才浮出
                        active
                          ? "opacity-100"
                          : "pointer-events-none opacity-0 group-hover:pointer-events-auto group-hover:opacity-100 group-focus-within:pointer-events-auto group-focus-within:opacity-100"
                      )}
                      title="关闭标签页"
                      aria-label={`关闭 ${meta.title}`}
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                );
              })}
            </div>
          ) : (
            <div className="min-w-0 flex-1 truncate px-1 text-ui-base text-foreground-subtle">
              动态
            </div>
          )}

          <div className="flex shrink-0 items-center gap-0.5">
            {onToggleFocusMode ? (
              <button
                type="button"
                onClick={onToggleFocusMode}
                className={classNames(
                  iconBtnClass,
                  isFocusMode && "bg-selected text-foreground"
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
            {previewUrl ? (
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

      {/* 工作区内容：工具条 + 预览。
          ZCode PreviewPane.tsx:1729 —— 内容区不套内缩卡片，直接 min-h-0 flex-1 铺满面板。 */}
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-background">
        {paneMode === "source" && selectedSource ? (
          // source tab（patch / text / image）：直接复用工具预览面板，渲染契约与
          // 右侧工具 diff 完全一致（TASKS.md §1.2 验收：与现有 diff 一模一样）。
          <div className="min-h-0 flex-1 overflow-hidden">
            <ToolPreviewPanel
              source={selectedSource.source}
              fileUrl={selectedSource.fileUrl}
              unavailableReason={selectedSource.unavailableReason}
              output={selectedSource.output}
              status={selectedSource.status}
              onClose={() => closeTab(selectedSource.key)}
            />
          </div>
        ) : paneMode === "file" && showFileBrowser && selectedFile ? (
          <>
            {/* 文件预览头：单行 h-10 面包屑 + ghost 操作区（对齐 ZCode
                PreviewPane.tsx:1531-1570）。旧版这里是「预览/源码 带框分段控件 +
                badge + 两行文件名 + 6 个图标」，一行信息被摊成三层。
                底部分隔线按 shouldShowPreviewHeaderDivider 条件渲染。 */}
            <div
              className={classNames(
                "flex h-10 shrink-0 items-center gap-8 px-3",
                showFileHeaderDivider && "border-b border-border/50"
              )}
            >
              <div className="flex min-w-0 flex-1 items-center gap-0.5 overflow-x-auto text-ui-base text-foreground-subtle [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
                {fileCrumb.parents.map((segment, index) => (
                  <Fragment key={`${segment}-${index}`}>
                    <span className="shrink-0">{segment}</span>
                    <ChevronRightIcon className="size-3.5 shrink-0 text-foreground-subtlest" />
                  </Fragment>
                ))}
                <span className="inline-flex min-w-0 items-center gap-1 text-foreground">
                  {selectedPdfIcon ? (
                    <img
                      src={selectedPdfIcon}
                      alt=""
                      className="size-4 shrink-0 rounded-sm object-contain"
                    />
                  ) : (
                    <span
                      className={classNames(
                        "flex size-4 shrink-0 items-center justify-center rounded-sm text-ui-xs font-bold leading-none",
                        FILE_KIND_TONE
                      )}
                    >
                      {fileKindBadge(selectedKind)}
                    </span>
                  )}
                  <span className="truncate">{fileCrumb.file}</span>
                </span>
              </div>

              <div className="flex shrink-0 items-center gap-1">
                {canSourceMode ? (
                  // 「预览 / 源码」保持内联可点，但去掉旧版那个带 border + 底色的
                  // 分段控件外壳 —— 头部里不再出现盒子（ZCode 的做法是收进「…」菜单，
                  // 这里为了不牺牲可发现性保留内联，仅去壳）。
                  <div
                    role="group"
                    aria-label="预览模式"
                    className="inline-flex shrink-0 items-center gap-0.5"
                  >
                    <button
                      type="button"
                      aria-pressed={viewMode === "preview"}
                      className={classNames(
                        "h-7 rounded-lg px-2 text-ui-sm font-medium transition-colors",
                        viewMode === "preview"
                          ? "bg-selected text-foreground"
                          : "text-foreground-subtle hover:bg-surface-hover hover:text-foreground"
                      )}
                      onClick={() => setViewMode("preview")}
                    >
                      预览
                    </button>
                    <button
                      type="button"
                      aria-pressed={viewMode === "source"}
                      className={classNames(
                        "h-7 rounded-lg px-2 text-ui-sm font-medium transition-colors",
                        viewMode === "source"
                          ? "bg-selected text-foreground"
                          : "text-foreground-subtle hover:bg-surface-hover hover:text-foreground"
                      )}
                      onClick={() => setViewMode("source")}
                    >
                      源码
                    </button>
                  </div>
                ) : null}

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
            </div>

            <div
              className="tab-content-enter flex min-h-0 flex-1 flex-col overflow-hidden bg-background"
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
              className="h-full"
            />
          </div>
        )}
      </div>

      <PlanView plan={plan} ref={planRef} />
    </motion.div>
  );
});

const ActionViewComp = memo(ActionViewInner);

// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-expect-error
const ActionView: typeof ActionViewComp & {
  useActionView: typeof useActionView;
} = ActionViewComp;
ActionView.useActionView = useActionView;

export default ActionView;
