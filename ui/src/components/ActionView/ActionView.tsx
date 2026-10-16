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
  Link2,
  Maximize2,
  Minimize2,
  MoreHorizontalIcon,
  RefreshCw,
  Star,
  X,
} from "lucide-react";
import { useMemoizedFn } from "ahooks";
import FileList from "./FileList";
import { PlanView, PlanViewAction } from "../PlanView";
import { PanelItemType } from "../ActionPanel";
import { copyText, downloadFile, showMessage } from "@/utils";
import { iconType } from "@/utils/constants";
import {
  AGENT_DIRECTORY_TAB_KEY,
  agentTabKey,
  collectWorkspaceFiles,
  findWorkspaceFileByPath,
  isAgentDirectoryTab,
  isAgentTab,
  isSourceTab,
  isTaskTab,
  resolveWorkspacePaneMode,
  taskTabKey,
  upsertWorkspaceTab,
  workspaceFileKey,
  workspaceTabKey,
  workspaceTabOpenedAt,
  type WorkspaceAgentTab,
  type WorkspaceTaskTab,
  type WorkspaceFileItem,
  type WorkspaceSourceTab,
  type WorkspaceTab,
} from "./workspaceFiles";
import {
  pathBreadcrumb,
  sourceTabKey,
  type CodeViewerSource,
} from "@/lib/codeViewerSource";
import { ToolPreviewPanel } from "../Dialogue/ToolPreviewPanel";
import { FilePreviewHeaderSlotContext } from "../ActionPanel/filePreviewHeaderSlot";
import { SubAgentDirectoryPanel } from "../Dialogue/SubAgentDirectoryPanel";
import { WorkspaceTabOverview } from "./WorkspaceTabOverview";
import { WorkspaceTabIcon } from "./WorkspaceTabIcon";
import { TabTitleTooltip } from "./TabTitleTooltip";
import { resolveWorkspaceTabDisplay } from "./workspaceTabDisplay";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { SubAgentRosterItem } from "@/utils/chat/subAgentRoster";
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

/**
 * 新 tab 的打开时刻。
 *
 * 命中已有 tab 时**沿用原值** —— 时间语义是「什么时候开的」，
 * 反复点同一个文件不应该把「标签页列表」里的时间一直刷成「刚刚」。
 */
function openedAtFor(prev: WorkspaceTab[], key: string): number {
  const existing = prev.find((item) => workspaceTabKey(item) === key);
  return (existing && workspaceTabOpenedAt(existing)) || Date.now();
}

/**
 * 打开一个子智能体 tab 的入参。
 *
 * `toolCallId` 是身份键（`agentTabKey`）；`tool/chat` 只是**打开瞬间的兜底快照**，
 * 真正的渲染数据由宿主在渲染时按 `toolCallId` 重新解析（流式父卡每秒在变）。
 */
export type OpenAgentTabPayload = {
  toolCallId: string;
  title: string;
  subagentType?: string;
  runInBackground?: boolean;
  tool: CHAT.Task;
  chat: CHAT.ChatItem;
};

export type ActionViewRef = PlanViewAction & {
  setFilePreview: (file?: CHAT.TFile) => void;
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
  /** 打开（或激活）一个子智能体详情 tab —— 与文件预览同一套挂载机制 */
  openAgentTab: (payload: OpenAgentTabPayload) => void;
  /** 打开（或激活）子智能体目录 tab（单例） */
  openAgentDirectoryTab: () => void;
  /** 打开（或激活）一个任务预览 tab（原「动态」跟随） */
  openTaskTab: (payload: OpenTaskTabPayload) => void;
  /** 按身份键关闭一个 tab（子智能体详情面板自己的关闭按钮走这条） */
  closeWorkspaceTab: (key: string) => void;
};

/**
 * 打开一个任务预览 tab 的入参。
 *
 * `taskKey` 是身份键（任务的稳定身份），`task` 只是**打开瞬间的兜底快照** ——
 * 流式过程中任务字段不断补全，真正的渲染数据由宿主在渲染时按 `taskKey` 回找。
 */
export type OpenTaskTabPayload = {
  taskKey: string;
  title: string;
  task: CHAT.Task;
};

const useActionView = () => {
  const ref = useRef<ActionViewRef>(null);
  return ref;
};

type ActionViewProps = {
  title?: React.ReactNode;
  className?: string;
  taskList?: PanelItemType[];
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
  /** 父级待打开的子智能体 tab（时间线 / 智能体面板点来的） */
  pendingAgentTab?: OpenAgentTabPayload | null;
  onPendingAgentTabConsumed?: () => void;
  /** 父级待打开的子智能体目录 tab（右上角面板页脚点来的） */
  pendingAgentDirectoryTab?: boolean;
  onPendingAgentDirectoryTabConsumed?: () => void;
  /** 父级待打开的任务预览 tab（点工具卡推来，代替旧的「动态」跟随） */
  pendingTaskTab?: OpenTaskTabPayload | null;
  onPendingTaskTabConsumed?: () => void;
  /** 子智能体名册：目录 tab 的数据源（与右上角面板同源） */
  agentRoster?: SubAgentRosterItem[];
  /** 目录行点击：由宿主解析并打开对应子智能体 tab */
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  /**
   * 子智能体详情 tab 的内容。
   *
   * 用 render prop 而不是把 tool/chat 塞进 tab：详情面板要跟着流式父卡实时刷新，
   * 而宿主（ChatView）才持有最新的 chatList 与回调集合。ActionView 只负责
   * 「哪个 tab 在前台」，内容由宿主给。
   */
  renderAgentTab?: (tab: WorkspaceAgentTab) => React.ReactNode;
  /** 任务预览 tab 的内容（原「动态」），同样由宿主按 taskKey 回找最新任务 */
  renderTaskTab?: (tab: WorkspaceTaskTab) => React.ReactNode;
  /**
   * tab 数量变化（含初始挂载）。宿主据此在**最后一个 tab 被关掉时收起整个工作区** ——
   * 旧的「动态」空态卡已删掉，没有 tab 就不该有工作区。
   */
  onTabsChange?: (count: number) => void;
  workspaceCaption?: string;
  plan?: CHAT.Plan;
  isFocusMode?: boolean;
  onToggleFocusMode?: () => void;
  onClose?: () => void;
  ref?: React.Ref<ActionViewRef>;
};

const ActionViewInner = forwardRef<ActionViewRef, ActionViewProps>((props, ref) => {
  const {
    className,
    onClose,
    taskList,
    plan,
    isFocusMode,
    onToggleFocusMode,
    pendingPreviewFile,
    onPendingPreviewFileConsumed,
    pendingSourceTab,
    onPendingSourceTabConsumed,
    pendingAgentTab,
    onPendingAgentTabConsumed,
    pendingAgentDirectoryTab,
    onPendingAgentDirectoryTabConsumed,
    pendingTaskTab,
    onPendingTaskTabConsumed,
    agentRoster,
    onOpenAgent,
    renderAgentTab,
    renderTaskTab,
    onTabsChange,
  } = props;

  const planRef = useRef<PlanViewAction>(null);
  const tabsRef = useRef<HTMLDivElement>(null);
  const [openTabs, setOpenTabs] = useState<WorkspaceTab[]>([]);
  const [selectedKey, setSelectedKey] = useState<string>("");
  const [refreshToken, setRefreshToken] = useState(0);
  const [viewMode, setViewMode] = useState<"preview" | "source">("preview");
  /**
   * tab 条左右是否还有可滚动内容 —— 决定边缘渐隐遮罩挂哪一侧。
   * 对齐 ZCode `AnimatedSidePanePanel.tsx:968-984` 的 `tabsScrollMaskEdges`。
   */
  const [tabsMask, setTabsMask] = useState({
    left: false,
    right: false,
  });

  /**
   * 文件预览头部的插槽节点（给 renderer 用 portal 挂「预览专属」按钮）。
   *
   * 典型是 HTML 预览的「选择网页元素」：picker 要挂 iframe 内部，逻辑只能在
   * `HTMLRenderer` 里；但按钮要和「预览 / 源码 / 复制链接 / 刷新 / 下载」同列，
   * 所以由这里出一个空节点 + Context（见 `filePreviewHeaderSlot.tsx`）。
   * 用 state 而不是 ref：节点挂上/卸载要让消费方重渲染一次。
   */
  const [headerSlotNode, setHeaderSlotNode] = useState<HTMLElement | null>(null);
  const fileHeaderSlot = useMemo(() => ({ node: headerSlotNode }), [headerSlotNode]);

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
    // source / agent / task / directory tab 不参与 catalog 合并（它们没有产物身份），原样透传。
    return openTabs.map((tab) => {
      if (
        isSourceTab(tab) ||
        isAgentTab(tab) ||
        isAgentDirectoryTab(tab) ||
        isTaskTab(tab)
      ) {
        return tab;
      }
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
        key,
        openedAt: openedAtFor(prev, key),
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
  });

  /**
   * 打开 / 激活一个子智能体详情 tab。
   *
   * 与 `openFileInTab` 平行：身份键是 `agentTabKey(toolCallId)`，同一子智能体
   * 重复点开只切前台、不新增 tab（ZCode `openSubagentSessionSidePane` 同款幂等）。
   * 命中已有 tab 时**刷新兜底快照**，避免历史裁剪后旧快照一直生效。
   */
  const openAgentInTab = useMemoizedFn((payload: OpenAgentTabPayload) => {
    if (!payload.toolCallId) {
      return;
    }
    const key = agentTabKey(payload.toolCallId);
    setOpenTabs((prev) => {
      const existing = prev.findIndex((item) => workspaceTabKey(item) === key);
      const next: WorkspaceAgentTab = {
        kind: "agent",
        key,
        toolCallId: payload.toolCallId,
        title: payload.title || "子智能体",
        subagentType: payload.subagentType,
        runInBackground: payload.runInBackground,
        fallbackTool: payload.tool,
        fallbackChat: payload.chat,
        openedAt: openedAtFor(prev, key),
      };
      if (existing >= 0) {
        const copy = [...prev];
        copy[existing] = next;
        return copy;
      }
      return [...prev, next];
    });
    setSelectedKey(key);
  });

  /** 打开 / 激活子智能体目录 tab（单例） */
  const openAgentDirectoryInTab = useMemoizedFn(() => {
    setOpenTabs((prev) => {
      const existing = prev.findIndex(
        (item) => workspaceTabKey(item) === AGENT_DIRECTORY_TAB_KEY
      );
      if (existing >= 0) {
        return prev;
      }
      return [
        ...prev,
        {
          kind: "agent-directory",
          key: AGENT_DIRECTORY_TAB_KEY,
          openedAt: Date.now(),
        },
      ];
    });
    setSelectedKey(AGENT_DIRECTORY_TAB_KEY);
  });

  /**
   * 打开 / 激活一个任务预览 tab（原「动态」跟随）。
   *
   * 身份键是 `taskTabKey(taskKey)`：同一个任务重复点开只切前台、不新增 tab。
   * tab **只存身份**，实时任务由宿主在渲染时回找 —— 流式过程中任务字段不断补全，
   * 存快照会让预览停在打开那一刻。
   *
   * 另外会顶替「已经以文件 tab 打开着的同一主产物」：两者渲染的是同一份内容
   * （见 `upsertWorkspaceTab`），留着就是两个一模一样的预览 tab。
   */
  const openTaskInTab = useMemoizedFn((payload: OpenTaskTabPayload) => {
    if (!payload.taskKey) {
      return;
    }
    const key = taskTabKey(payload.taskKey);
    setOpenTabs((prev) => {
      const next: WorkspaceTaskTab = {
        kind: "task",
        key,
        taskKey: payload.taskKey,
        title: payload.title || "任务",
        fallbackTask: payload.task,
        openedAt: openedAtFor(prev, key),
      };
      return upsertWorkspaceTab(prev, next);
    });
    setSelectedKey(key);
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

  /**
   * 关闭除 `keepKey` 外的所有 tab（右键菜单「关闭其他标签」）。
   *
   * 关完一定把选中项钉回 `keepKey` —— 被保留的那个 tab 如果在列表尾部，
   * `closeTab` 的「右邻优先」落点会把它自己顶掉，出现「关了其他的，结果当前这个也没选中」。
   */
  const closeOtherTabs = useMemoizedFn((keepKey: string) => {
    setOpenTabs((prev) =>
      prev.filter((item) => workspaceTabKey(item) === keepKey)
    );
    setSelectedKey(keepKey);
  });

  /** 关闭全部 tab。全关光之后由宿主（`onTabsChange` → 0）收起整个工作区。 */
  const closeAllTabs = useMemoizedFn(() => {
    setOpenTabs([]);
    setSelectedKey("");
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
      const next = {
        ...resolved,
        openedAt: openedAtFor(prev, nextKey),
      };
      // 同一个文件只留一个预览 tab（详见 `upsertWorkspaceTab` 的注释）：
      // 任务 tab 与文件 tab 身份键不同，但渲染的是同一份内容。
      return upsertWorkspaceTab(prev, next);
    });
    setSelectedKey(workspaceFileKey(resolved));
    setViewMode("preview");
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

  // 时间线 / 右上角「智能体」面板推来的子智能体 tab。同样「挂载后消费一次」。
  useEffect(() => {
    if (!pendingAgentTab) return;
    openAgentInTab(pendingAgentTab);
    onPendingAgentTabConsumed?.();
  }, [pendingAgentTab, openAgentInTab, onPendingAgentTabConsumed]);

  useEffect(() => {
    if (!pendingAgentDirectoryTab) return;
    openAgentDirectoryInTab();
    onPendingAgentDirectoryTabConsumed?.();
  }, [
    pendingAgentDirectoryTab,
    openAgentDirectoryInTab,
    onPendingAgentDirectoryTabConsumed,
  ]);

  useEffect(() => {
    if (!pendingTaskTab) return;
    openTaskInTab(pendingTaskTab);
    onPendingTaskTabConsumed?.();
  }, [pendingTaskTab, openTaskInTab, onPendingTaskTabConsumed]);

  // tab 数量上报宿主：最后一个 tab 被关掉时由宿主收起整个工作区。
  //
  // 宿主可能每轮渲染都传新的回调，所以用 ref 固定住，让这个 effect 只在
  // **数量真的变化**时触发（否则每次父级重渲染都会重复上报同一个数字）。
  const onTabsChangeRef = useRef(onTabsChange);
  useEffect(() => {
    onTabsChangeRef.current = onTabsChange;
  }, [onTabsChange]);
  useEffect(() => {
    onTabsChangeRef.current?.(openTabs.length);
  }, [openTabs.length]);

  // 没有任务产物时认为会话工作区已清空，关闭旧**文件** tab；有产物时保留用户已打开
  // 的 tab，避免每次流式 taskList 更新都重置预览位置。
  //
  // 子智能体 tab / 目录 tab / 任务预览 tab 不受这条规则影响：它们的生命周期跟会话与
  // 用户操作走，跟「这轮有没有产物文件」无关（否则纯子 Agent 任务一结束、或某个
  // 无产物的工具任务关掉产物列表，tab 就被清掉）。
  useEffect(() => {
    if (taskList?.length || catalog.length) {
      return;
    }
    const kept = openTabs.filter(
      (tab) => isAgentTab(tab) || isAgentDirectoryTab(tab) || isTaskTab(tab)
    );
    if (kept.length === openTabs.length) {
      return;
    }
    setOpenTabs(kept);
    setSelectedKey((current) => {
      if (!current) {
        return current;
      }
      if (kept.some((tab) => workspaceTabKey(tab) === current)) {
        return current;
      }
      return kept.length ? workspaceTabKey(kept[kept.length - 1]) : "";
    });
  }, [taskList, catalog.length, openTabs]);

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
  }, [selectedKey, files.length]);

  useImperativeHandle(ref, () => {
    // 暴露给 ChatView 的命令只改变 ActionView 自己的视图状态，不把文件目录
    // 逻辑泄漏到父组件。工作区没有「模式」之分：旧的 `changeActionView(follow)`
    // 已随「动态」伪模式一起删掉，内容区完全由选中的 tab 决定。
    return {
      ...planRef.current!,
      setFilePreview: (file) => {
        if (!file) {
          return;
        }
        openFileInTab(file);
      },
      openSourceTab: (tab) => {
        // parent 的 chat 轮次尚未挂载时，tab 也得先落盘，因此复用 pending 通道：
        // 这里直接写 state（ActionView 已挂载才会被调用），无需父级缓存。
        openSourceInTab(tab);
      },
      openAgentTab: (payload) => {
        openAgentInTab(payload);
      },
      openAgentDirectoryTab: () => {
        openAgentDirectoryInTab();
      },
      openTaskTab: (payload) => {
        openTaskInTab(payload);
      },
      closeWorkspaceTab: (key) => {
        closeTab(key);
      },
    };
  });

  const selectedTab: WorkspaceTab | undefined = useMemo(() => {
    // selectedKey 失效时回退到最新 tab，保证目录更新或删除后仍有稳定的可视内容。
    if (!files.length) return undefined;
    if (!selectedKey) return files[files.length - 1];
    return files.find((f) => workspaceTabKey(f) === selectedKey) || files[files.length - 1];
  }, [files, selectedKey]);

  // 五种 tab 分流：产物文件 / 工具 source / 任务预览 / 子智能体详情 / 子智能体目录。
  const isFileTabSelected = Boolean(
    selectedTab && !isSourceTab(selectedTab) && !isTaskTab(selectedTab) && !isAgentTab(selectedTab) && !isAgentDirectoryTab(selectedTab)
  );
  const selectedFile: WorkspaceFileItem | undefined = isFileTabSelected
    ? (selectedTab as WorkspaceFileItem)
    : undefined;
  const selectedSource: WorkspaceSourceTab | undefined =
    selectedTab && isSourceTab(selectedTab) ? selectedTab : undefined;
  const selectedTask: WorkspaceTaskTab | undefined =
    selectedTab && isTaskTab(selectedTab) ? selectedTab : undefined;
  const selectedAgent: WorkspaceAgentTab | undefined =
    selectedTab && isAgentTab(selectedTab) ? selectedTab : undefined;

  const showFileBrowser = files.length > 0;
  /**
   * 内容区分派：**只看选中的 tab**。
   *
   * 没有第二套「模式」了 —— 旧的 `panelMode: "follow"`（动态跟随）已删掉，
   * 空态卡随之消失；`empty` 的含义是「宿主该把整个工作区收起」。
   */
  const paneMode = resolveWorkspacePaneMode(selectedTab);
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
      {/* 顶栏：最左「标签页列表」+ 已打开 tab；内容区完全由选中的 tab 决定。
          ZCode `AnimatedSidePanePanel.tsx:946` 的 TabsList 是
          `border-b border-border/50 bg-transparent` —— **半透明分隔线 + 无底色**。
          全量 `border-border` 配 `bg-surface/30` 会把这一行画成一条明显的横杠。 */}
      <div className="reactor-workspace-toolbar flex h-10 shrink-0 items-center px-3">
        <div className="flex min-w-0 flex-1 items-center gap-2">
          {/* 最左固定位：标签页列表浮层（对齐 ZCode `AnimatedSidePanePanel.tsx:957-960`
              的 `p-2` 固定槽）。放在滚动容器**之外**，溢出时也不会跟着滚走。 */}
          {files.length > 0 ? (
            <div className="flex shrink-0 items-center">
              <WorkspaceTabOverview
                tabs={files}
                activeKey={workspaceTabKey(selectedTab || files[0])}
                onActivate={(key) => {
                  setSelectedKey(key);
                }}
                onCloseTab={closeTab}
              />
            </div>
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
              {/* 没有「动态」按钮了：旧的动态跟随模式（`panelMode: "follow"`）已删掉，
                  点工具卡现在开一个真实的「任务预览 tab」，和文件 tab 同一条 tab 栏。 */}
              {files.map((tab) => {
                const key = workspaceTabKey(tab);
                const active = key === workspaceTabKey(selectedTab || files[0]);
                // 图标 / 标题 / diff 徽章统一由 workspaceTabDisplay 解析 —— 与
                // 「标签页列表」浮层共用同一份，加 tab 类型时不会漏改一处。
                const display = resolveWorkspaceTabDisplay(tab);
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
                      // `pr-5` 给右侧关闭按钮**固定**留位（X 是 absolute，size-4 @ right-1）：
                      // 标题用 `text-overflow: ellipsis` 截断，留位固定，省略号不会压到 X 上，
                      // 也不会因为 hover 时 X 浮出而重新截断。
                      "reactor-workspace-tab group relative flex h-7 max-w-[156px] min-w-[110px] flex-[0_1_9.75rem] items-center overflow-hidden rounded-lg border border-transparent pl-2 pr-5 transition-colors",
                      active
                        ? "bg-selected text-foreground"
                        : "text-foreground-subtle hover:bg-surface-hover hover:text-foreground"
                    )}
                  >
                    <TabTitleTooltip
                      label={display.title}
                      onActivate={() => {
                        setSelectedKey(key);
                      }}
                    >
                      <WorkspaceTabIcon spec={display.icon} />
                      <span className="reactor-workspace-tab-title min-w-0 text-ui-sm font-medium">
                        {display.title}
                      </span>
                    </TabTitleTooltip>
                    {display.diffBadge ? (
                      <span className="shrink-0 pl-1 text-ui-xs tabular-nums text-foreground-subtlest">
                        {display.diffBadge}
                      </span>
                    ) : null}
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
                      aria-label={`关闭 ${display.title}`}
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                );
              })}
            </div>
          ) : null}

          {/* tab 条最右的批量操作菜单。ZCode 用的是 tab 右键菜单
              （`SidePaneTabTrigger.tsx:189-195`），但 Web 上右键没有任何视觉提示、
              且用户预期是浏览器原生菜单，所以换成同款三项的可见 `…` 入口。 */}
          {files.length > 0 ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  className="flex size-7 shrink-0 items-center justify-center rounded-lg text-foreground-subtle transition-colors hover:bg-surface-hover hover:text-foreground"
                  aria-label="标签页操作"
                  title="标签页操作"
                  data-testid="workspace-tab-actions"
                >
                  <MoreHorizontalIcon className="size-4" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent
                align="end"
                side="bottom"
                sideOffset={6}
                className="w-44 rounded-lg border border-popover-border bg-popover p-1 shadow-md"
              >
                <DropdownMenuItem
                  disabled={files.length < 2}
                  onSelect={() =>
                    closeOtherTabs(workspaceTabKey(selectedTab || files[0]))
                  }
                >
                  关闭其他标签
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => closeAllTabs()}>
                  关闭所有标签
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}

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
        {paneMode === "agent-directory" ? (
          // 子智能体目录：与右上角「智能体」面板同源的名册，按「正在运行 / 已结束」分段。
          <div className="min-h-0 flex-1 overflow-hidden">
            <SubAgentDirectoryPanel items={agentRoster || []} onOpen={onOpenAgent} />
          </div>
        ) : paneMode === "agent" && selectedAgent ? (
          // 子智能体详情：内容由宿主给（它持有最新 chatList 与回调），
          // ActionView 只决定「哪个 tab 在前台」。
          <div className="min-h-0 flex-1 overflow-hidden">
            {renderAgentTab?.(selectedAgent)}
          </div>
        ) : paneMode === "task" && selectedTask ? (
          // 任务预览 tab（原「动态」）：内容由宿主给 —— 流式过程中任务字段不断补全，
          // 必须按稳定身份回找最新任务，不能把打开瞬间的快照存进 tab。
          <div className="min-h-0 flex-1 overflow-hidden">
            {renderTaskTab?.(selectedTask)}
          </div>
        ) : paneMode === "source" && selectedSource ? (
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
          <FilePreviewHeaderSlotContext.Provider value={fileHeaderSlot}>
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

                {/* 预览专属操作的插槽：HTML 预览的「选择网页元素」由 HTMLRenderer
                    用 portal 渲染到这里，和这一排按钮同列（空着时 `empty:hidden`
                    不占位、也不多吃一个 gap）。 */}
                <div
                  ref={setHeaderSlotNode}
                  className="flex shrink-0 items-center gap-0.5 empty:hidden"
                  data-testid="workspace-file-header-slot"
                />

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
          </FilePreviewHeaderSlotContext.Provider>
        ) : null}
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
