import { formatTimestamp } from "@/utils";
import {
  basenameOfPath,
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
  return workspaceRelativePath(file) ||
    file.resourceKey ||
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
