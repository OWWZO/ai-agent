import { formatTimestamp } from "@/utils";
import { getTaskFiles } from "@/utils/taskArtifacts";
import type { PanelItemType } from "../ActionPanel";

const MESSAGE_TYPES_WITH_FILES = [
  "file",
  "code",
  "html",
  "markdown",
  "result",
  "data_analysis",
  "ui_tree",
  "tool_result",
  "image_generation",
];

export type WorkspaceFileItem = CHAT.TFile & {
  messageTime?: string;
  task: PanelItemType;
};

export function collectWorkspaceFiles(
  taskList?: PanelItemType[]
): WorkspaceFileItem[] {
  const seen = new Map<string, WorkspaceFileItem>();
  for (const task of taskList || []) {
    if (!MESSAGE_TYPES_WITH_FILES.includes(task.messageType)) {
      continue;
    }
    for (const file of getTaskFiles(task)) {
      const key = workspaceFileKey(file);
      if (!key || seen.has(key)) {
        continue;
      }
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
 * 工作区文件身份：优先稳定 resourceKey，其次 URL，避免同名产物互相覆盖。
 */
export function workspaceFileKey(
  file?: Pick<CHAT.TFile, "resourceKey" | "name" | "url" | "downloadUrl"> | null
) {
  if (!file) {
    return "";
  }
  return file.resourceKey || file.url || file.downloadUrl || file.name || "";
}
