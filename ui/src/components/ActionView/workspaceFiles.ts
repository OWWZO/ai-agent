import { formatTimestamp } from "@/utils";
import {
  basenameOfPath,
  getPrimaryTaskFile,
  getTaskFiles,
  normalizeRelativePath,
} from "@/utils/taskArtifacts";
import { resolveTaskResultMap } from "@/utils/chat/toolCalls";
import type { PanelItemType } from "../ActionPanel";

const MESSAGE_TYPES_WITH_FILES = [
  "file",
  "code",
  "html",
  "markdown",
  "result",
  "data_analysis",
  "tool_result",
  "image_generation",
];

function isCanvasPublishPreviewTask(task: PanelItemType | CHAT.Task): boolean {
  const resultMap = resolveTaskResultMap(task);
  return String(resultMap.toolName || "").toLowerCase() === "canvas_publish";
}

export type WorkspaceFileItem = CHAT.TFile & {
  messageTime?: string;
  task: PanelItemType;
};

/**
 * 侧栏 tab 的两种来源（Stage 4）。
 *
 * - `file`   —— 会话产物文件（原有形态，带可读 URL）
 * - `source` —— 由工具卡片推来的 `CodeViewerSource`（patch / text / image / 无 URL 的 file）
 *
 * 之所以要单独的 `source` 形态：会话产物 tab 的身份是 `workspaceFileKey()`（依赖
 * URL / resourceKey），而 patch 这类内容根本没有落盘文件，塞不进产物模型。
 * 两者共用一个 tab 条，但身份键与渲染分派分开。
 */
export type WorkspaceSourceTab = WorkspaceTabMeta & {
  kind: "source";
  key: string;
  source: import("@/lib/codeViewerSource").CodeViewerSource;
  fileUrl?: string;
  unavailableReason?: string;
  output?: string[];
  status?: "running" | "ok" | "error";
};

export type WorkspaceTab =
  | (WorkspaceFileItem & { kind?: "file"; key?: string } & WorkspaceTabMeta)
  | WorkspaceSourceTab
  | WorkspaceAgentTab
  | WorkspaceAgentDirectoryTab
  | WorkspaceTaskTab;

/**
 * 所有 tab 共有的元数据。
 *
 * `openedAt` 只服务于「标签页列表」的相对时间（对齐 ZCode 的 `刚刚 / X 分`）；
 * **重新激活一个已存在的 tab 不刷新它** —— 时间语义是「什么时候开的」，
 * 不是「最后一次看」。
 */
export type WorkspaceTabMeta = {
  openedAt?: number;
};

/**
 * 子智能体详情 tab。
 *
 * 身份键只用 `toolCallId`（`agentTabKey`），**不存 tool 本体** —— 父卡在流式过程中
 * 每秒都在变，把快照存进 tab 会让详情面板停在打开那一刻。渲染时由宿主
 * （ChatView）按 `toolCallId` 取实时卡；`fallbackTool/fallbackChat` 只在
 * 父卡已被历史裁剪（例如后台子 Agent 的老会话）时兜底，保证 tab 不白屏。
 */
export type WorkspaceAgentTab = WorkspaceTabMeta & {
  kind: "agent";
  key: string;
  toolCallId: string;
  title: string;
  subagentType?: string;
  runInBackground?: boolean;
  fallbackTool: CHAT.Task;
  fallbackChat: CHAT.ChatItem;
};

/** 子智能体目录 tab（单例：整个工作区只允许一个目录 tab） */
export type WorkspaceAgentDirectoryTab = WorkspaceTabMeta & {
  kind: "agent-directory";
  key: string;
};

/**
 * 任务预览 tab（原「动态」跟随模式）。
 *
 * 旧的「动态」是工作区的一个**伪模式**（`panelMode: "follow"`）：tab 条上有一个
 * 同名按钮，没有选中任何文件时内容区就是一张空态卡。它已被删掉 —— 点工具卡
 * 现在直接开一个真实的 tab，和文件 / 子智能体详情同机制。
 *
 * 身份键是任务的稳定身份（`getStableTaskIdentity`）；渲染时由宿主按它回找
 * 最新任务（流式过程中任务会不断补全字段），`fallbackTask` 只兜底。
 */
export type WorkspaceTaskTab = WorkspaceTabMeta & {
  kind: "task";
  key: string;
  taskKey: string;
  title: string;
  fallbackTask: CHAT.Task;
};

export const AGENT_DIRECTORY_TAB_KEY = "agent-directory";

/** 子智能体详情 tab 的身份键：同一 toolCallId 只会有一个 tab */
export function agentTabKey(toolCallId: string): string {
  return `agent:${toolCallId}`;
}

/** 任务预览 tab 的身份键：同一任务只会有一个 tab */
export function taskTabKey(taskKey: string): string {
  return `task:${taskKey}`;
}

/** tab 打开时刻（ms epoch）：标签页列表的相对时间用；缺失时列表不渲染这一段 */
export function workspaceTabOpenedAt(tab: WorkspaceTab): number | undefined {
  return (tab as WorkspaceTabMeta).openedAt;
}

/** 统一取 tab 身份键：file tab 走产物 key，其余各自带 key。 */
export function workspaceTabKey(tab: WorkspaceTab): string {
  const kind = (tab as { kind?: string }).kind;
  if (kind) {
    return (
      tab as
        | WorkspaceSourceTab
        | WorkspaceAgentTab
        | WorkspaceAgentDirectoryTab
        | WorkspaceTaskTab
    ).key;
  }
  return workspaceFileKey(tab as WorkspaceFileItem);
}

export function isSourceTab(tab: WorkspaceTab): tab is WorkspaceSourceTab {
  return (tab as WorkspaceSourceTab).kind === "source";
}

export function isAgentTab(tab: WorkspaceTab): tab is WorkspaceAgentTab {
  return (tab as WorkspaceAgentTab).kind === "agent";
}

export function isAgentDirectoryTab(
  tab: WorkspaceTab
): tab is WorkspaceAgentDirectoryTab {
  return (tab as WorkspaceAgentDirectoryTab).kind === "agent-directory";
}

export function isTaskTab(tab: WorkspaceTab): tab is WorkspaceTaskTab {
  return (tab as WorkspaceTaskTab).kind === "task";
}

/**
 * 任务 tab 正在预览的**产物文件身份**（拿不到就是空串）。
 *
 * 用 tab 自带的兜底快照算：任务 tab 是在打开那一刻用当时的任务建的
 * （用户点工具卡 / 工作区自动跟随），那个快照已经带上 `artifactRefs` / `fileInfo`。
 * 流式后续补全的字段由宿主在渲染时回找，不影响这里判断「它预览的是哪个文件」。
 */
export function taskTabPrimaryFileKey(tab: WorkspaceTab): string {
  if (!isTaskTab(tab)) {
    return "";
  }
  const file = getPrimaryTaskFile(tab.fallbackTask);
  return file ? workspaceFileKey(file) : "";
}

/**
 * 一个 tab「正在预览的产物文件身份」；不预览任何文件时是空串。
 *
 * - 文件 tab：它自己就是那个文件；
 * - 任务 tab：它的**主产物**（任务 tab 的内容就是 ActionPanel 渲染那个主产物）；
 * - source（补丁 / 内联文本）、子智能体、目录：没有文件身份。
 */
export function tabPreviewFileKey(tab: WorkspaceTab): string {
  if (isTaskTab(tab)) {
    return taskTabPrimaryFileKey(tab);
  }
  if (isSourceTab(tab) || isAgentTab(tab) || isAgentDirectoryTab(tab)) {
    return "";
  }
  return workspaceFileKey(tab as WorkspaceFileItem);
}

/**
 * 把一个新 tab 合并进现有 tab 列表：**身份键幂等** + **同一个文件只留一个预览 tab**。
 *
 * 为什么需要跨类型去重：任务 tab 与文件 tab 的**身份键不同**
 * （`task:<任务身份>` vs 产物 key），但渲染的是**同一份内容** —— 任务 tab 的
 * ActionPanel 就是拿该任务的主产物去渲染的（`catalog` 里文件 tab 的 `task` 也是
 * 同一个来源任务）。于是「打开会话时自动跟随开出的任务 tab」+「用户点『打开文件』」
 * 会叠出两个标题相同、内容完全一样的 tab —— 用户看到的重复预览就是这个。
 *
 * 规则：谁**最后被显式打开**，谁占据那个位置 ——
 * - 身份键命中已有 tab → 原地更新（沿用 `openedAt`，幂等）；
 * - 已有另一个 kind 的 tab 正预览同一个文件 → **就地顶替**它（位置沿用，不跳到末尾）；
 * - 否则追加。
 *
 * source tab 不参与：补丁 / 内联文本是另一种视图，允许与文件 tab 并存。
 */
export function upsertWorkspaceTab(
  prev: WorkspaceTab[],
  next: WorkspaceTab
): WorkspaceTab[] {
  const nextKey = workspaceTabKey(next);
  if (!nextKey) {
    return prev;
  }
  const shadowKey = tabPreviewFileKey(next);

  let replaceAt = -1;
  const kept: WorkspaceTab[] = [];
  for (const item of prev) {
    if (workspaceTabKey(item) === nextKey) {
      kept.push(item);
      continue;
    }
    if (shadowKey && tabPreviewFileKey(item) === shadowKey) {
      if (replaceAt < 0) {
        replaceAt = kept.length;
      }
      continue;
    }
    kept.push(item);
  }

  const existing = kept.findIndex((item) => workspaceTabKey(item) === nextKey);
  if (existing >= 0) {
    const copy = [...kept];
    copy[existing] = next;
    return copy;
  }
  if (replaceAt >= 0) {
    const copy = [...kept];
    copy.splice(replaceAt, 0, next);
    return copy;
  }
  return [...kept, next];
}

/**
 * 工作区内容区渲染哪一屏 —— **由选中的 tab 决定，没有第二套模式**。
 *
 * `empty` 表示「没有选中任何 tab」，宿主（ChatView）据此**收起整个工作区**：
 * 旧的「动态」空态卡已被删掉，不再是「打开工作区但里面什么都没有」。
 *
 * 分派优先级：目录 > 子智能体 > 任务 > source > file。各 tab 身份互斥，
 * 正常不会同时命中，这里的顺序只是给「状态未及时同步」一个确定性结果。
 */
export function resolveWorkspacePaneMode(
  tab?: WorkspaceTab
): "empty" | "source" | "file" | "agent" | "agent-directory" | "task" {
  if (!tab) {
    return "empty";
  }
  if (isAgentDirectoryTab(tab)) {
    return "agent-directory";
  }
  if (isAgentTab(tab)) {
    return "agent";
  }
  if (isTaskTab(tab)) {
    return "task";
  }
  if (isSourceTab(tab)) {
    return "source";
  }
  return "file";
}

export type WorkspaceTreeDirNode = {
  kind: "dir";
  name: string;
  path: string;
  size: number;
  children: WorkspaceTreeNode[];
};

export type WorkspaceTreeFileNode = {
  kind: "file";
  name: string;
  path: string;
  size: number;
  file: WorkspaceFileItem;
};

export type WorkspaceTreeNode = WorkspaceTreeDirNode | WorkspaceTreeFileNode;

/**
 * 深度遍历任务树（含 Agent children），收集带产物的任务。
 */
export function flattenTasksWithFiles(
  tasks?: Array<PanelItemType | CHAT.Task | null | undefined> | null
): PanelItemType[] {
  const out: PanelItemType[] = [];
  const walk = (task?: PanelItemType | CHAT.Task | null) => {
    if (!task) {
      return;
    }
    if (
      MESSAGE_TYPES_WITH_FILES.includes(task.messageType) &&
      !isCanvasPublishPreviewTask(task as PanelItemType | CHAT.Task) &&
      getTaskFiles(task).length > 0
    ) {
      out.push(task as PanelItemType);
    }
    const children = (task as CHAT.Task).children;
    if (Array.isArray(children)) {
      for (const child of children) {
        walk(child);
      }
    }
  };
  for (const task of tasks || []) {
    walk(task);
  }
  return out;
}

/**
 * 将用户上传附件合成 file 任务，供侧栏/工作区与工具产物同一套 collectWorkspaceFiles 消费。
 */
export function buildUserUploadFileTask(
  files?: CHAT.TFile[] | null,
  messageTime?: string
): PanelItemType | null {
  if (!Array.isArray(files) || !files.length) {
    return null;
  }
  const fileInfo = files
    .filter((file) => Boolean(file?.name))
    .map((file) => ({
      fileName: file.name,
      domainUrl: file.previewUrl || file.url || file.downloadUrl || "",
      ossUrl: file.downloadUrl || file.url || file.previewUrl || "",
      fileSize: file.size,
      resourceKey: file.resourceKey,
      relativePath: file.relativePath,
      originFileName: file.originFileName,
      missing: file.missing,
      missingReason: file.missingReason,
      mimeType: file.mimeType,
    }));
  if (!fileInfo.length) {
    return null;
  }
  return {
    messageType: "file",
    messageTime: messageTime || "",
    resultMap: {
      command: "用户上传",
      fileInfo,
    },
  } as unknown as PanelItemType;
}

/**
 * 将会话级 artifact 清单包装为普通 fileListOnly 任务，复用现有文件聚合链。
 */
export function buildSessionFileManifestTask(
  files?: CHAT.TFile[] | null,
  messageTime?: string
): PanelItemType | null {
  const fileInfo = (files || [])
    .filter((file) => Boolean(file?.name))
    .map((file) => ({
      fileName: file.name,
      displayName: file.name,
      domainUrl: file.previewUrl || file.url || file.downloadUrl || "",
      ossUrl: file.downloadUrl || file.url || file.previewUrl || "",
      previewUrl: file.previewUrl || file.url || file.downloadUrl || "",
      downloadUrl: file.downloadUrl || file.url || file.previewUrl || "",
      fileSize: file.size,
      resourceKey: file.resourceKey,
      relativePath: file.relativePath,
      originFileName: file.originFileName,
      missing: file.missing,
      missingReason: file.missingReason,
      mimeType: file.mimeType,
      artifactType: file.type,
    }));
  if (!fileInfo.length) {
    return null;
  }
  return {
    messageType: "file",
    messageTime: messageTime || "",
    resultMap: {
      messageType: "file",
      fileListOnly: true,
      fileInfo,
    },
  } as unknown as PanelItemType;
}

/**
 * 收集单轮会话的文件任务，供流式更新时按 chat 对象复用历史结果。
 */
export function collectChatFileTasks(chat?: CHAT.ChatItem | null): PanelItemType[] {
  if (!chat) {
    return [];
  }

  const collected: PanelItemType[] = [];
  const uploadTask = buildUserUploadFileTask(
    chat.files,
    chat.startedAt || chat.requestId
  );
  if (uploadTask) {
    collected.push(uploadTask);
  }
  for (const group of chat.tasks || []) {
    collected.push(...flattenTasksWithFiles(group));
  }
  for (const group of chat.multiAgent?.tasks || []) {
    collected.push(...flattenTasksWithFiles(group));
  }
  if (chat.conclusion) {
    collected.push(...flattenTasksWithFiles([chat.conclusion]));
  }
  return collected;
}

/**
 * 从会话全部轮次 + 当前流式 taskList 聚合可展示任务（会话级，不清空历史轮次）。
 * 含用户上传 chat.files，避免上传图不进侧栏、终答相对引用解析失败。
 */
export function collectSessionFileTasks(
  chatList?: CHAT.ChatItem[] | null,
  liveTaskList?: PanelItemType[] | null
): PanelItemType[] {
  const collected: PanelItemType[] = [];
  for (const chat of chatList || []) {
    collected.push(...collectChatFileTasks(chat));
  }
  collected.push(...flattenTasksWithFiles(liveTaskList));
  return collected;
}

export function collectWorkspaceFiles(
  taskList?: PanelItemType[]
): WorkspaceFileItem[] {
  const seen = new Map<string, WorkspaceFileItem>();
  // 会话级列表可能已扁平化，仍再 walk 一次 children 防止遗漏
  for (const task of flattenTasksWithFiles(taskList)) {
    for (const file of getTaskFiles(task)) {
      const key = workspaceFileKey(file);
      if (!key || seen.has(key)) {
        continue;
      }
      // 后写覆盖：同 key 保留较新 task（会话后轮优先）
      seen.set(key, {
        ...file,
        task,
        messageTime: formatTimestamp(task.messageTime),
      });
    }
  }
  return [...seen.values()];
}

/**
 * 工作区文件身份：优先稳定 resourceKey，其次相对路径，避免同名产物互相覆盖。
 */
export function workspaceFileKey(
  file?: Pick<CHAT.TFile, "resourceKey" | "relativePath" | "name" | "url" | "downloadUrl"> | null
) {
  if (!file) {
    return "";
  }
  return file.resourceKey ||
    workspaceRelativePath(file) ||
    file.url ||
    file.downloadUrl ||
    file.name ||
    "";
}

export function workspaceRelativePath(
  file?: Pick<CHAT.TFile, "relativePath" | "originFileName" | "name"> | null
): string {
  if (!file) {
    return "";
  }
  return normalizeRelativePath(file.relativePath) ||
    normalizeRelativePath(file.originFileName) ||
    normalizeRelativePath(file.name) ||
    "";
}

export function formatWorkspaceBytes(size?: number | null): string {
  const value = size || 0;
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / (1024 * 1024)).toFixed(2)} MB`;
}

/**
 * 把一个工具入参里的绝对 / 相对路径映射到会话产物文件。
 *
 * 决策点 B（TASKS.md §5）：前端没有「按任意绝对路径读文件」的接口，工具读写的文件
 * 只有出现在会话产物清单里才可预览。匹配按「路径后缀」做，因为工具给的常是绝对路径
 * （`/workspace/src/a.ts`），而产物清单里是相对路径（`src/a.ts`）。
 */
export function findWorkspaceFileByPath(
  files: WorkspaceFileItem[] | CHAT.TFile[] | null | undefined,
  path: string | null | undefined
): WorkspaceFileItem | CHAT.TFile | undefined {
  const target = normalizeRelativePath(path);
  if (!target) return undefined;
  const list = files || [];
  // 优先精确命中，再退化为后缀命中（绝对路径 → 相对路径）
  let suffixHit: WorkspaceFileItem | CHAT.TFile | undefined;
  for (const file of list) {
    const relative = workspaceRelativePath(file) || normalizeRelativePath(file.name);
    if (!relative) continue;
    if (relative === target) return file;
    if (!suffixHit && (target.endsWith(`/${relative}`) || relative.endsWith(`/${target}`))) {
      suffixHit = file;
    }
  }
  return suffixHit;
}

/** 从产物文件取可读 URL（预览优先，其次原文件）。 */
export function workspaceFileUrl(
  file?: Pick<CHAT.TFile, "previewUrl" | "url" | "downloadUrl"> | null
): string {
  if (!file) return "";
  return file.previewUrl || file.url || file.downloadUrl || "";
}

function compareTreeNodes(left: WorkspaceTreeNode, right: WorkspaceTreeNode): number {
  if (left.kind !== right.kind) {
    return left.kind === "dir" ? -1 : 1;
  }
  return left.name.localeCompare(right.name, "zh");
}

function sortTree(nodes: WorkspaceTreeNode[]): WorkspaceTreeNode[] {
  const sorted = [...nodes].sort(compareTreeNodes);
  for (const node of sorted) {
    if (node.kind === "dir") {
      node.children = sortTree(node.children);
      node.size = node.children.reduce((sum, child) => sum + child.size, 0);
    }
  }
  return sorted;
}

export function buildWorkspaceTree(files: WorkspaceFileItem[]): WorkspaceTreeNode[] {
  const root: WorkspaceTreeNode[] = [];
  const dirs = new Map<string, WorkspaceTreeDirNode>();

  const ensureDir = (dirPath: string): WorkspaceTreeDirNode => {
    const existing = dirs.get(dirPath);
    if (existing) {
      return existing;
    }
    const parentPath = dirPath.includes("/")
      ? dirPath.slice(0, dirPath.lastIndexOf("/"))
      : "";
    const node: WorkspaceTreeDirNode = {
      kind: "dir",
      name: basenameOfPath(dirPath) || dirPath,
      path: dirPath,
      size: 0,
      children: [],
    };
    dirs.set(dirPath, node);
    if (!parentPath) {
      root.push(node);
    } else {
      ensureDir(parentPath).children.push(node);
    }
    return node;
  };

  for (const file of files) {
    const relative = workspaceRelativePath(file) || file.name || "未命名文件";
    const parts = relative.split("/").filter(Boolean);
    const fileName = parts.pop() || file.name || "未命名文件";
    const dirPath = parts.join("/");
    const node: WorkspaceTreeFileNode = {
      kind: "file",
      name: fileName,
      path: relative,
      size: file.size || 0,
      file,
    };
    if (!dirPath) {
      root.push(node);
    } else {
      ensureDir(dirPath).children.push(node);
    }
  }

  return sortTree(root);
}
