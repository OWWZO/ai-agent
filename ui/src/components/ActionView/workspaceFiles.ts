import { keyBy } from "lodash";
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
  let map: Record<string, WorkspaceFileItem> = {};
  const list = (taskList || []).reduce<WorkspaceFileItem[]>((pre, task) => {
    if (MESSAGE_TYPES_WITH_FILES.includes(task.messageType)) {
      const files: WorkspaceFileItem[] = getTaskFiles(task).map((file) => ({
        ...file,
        task,
        messageTime: formatTimestamp(task.messageTime),
      }));
      pre.push(
        ...files.filter((item) => !map[workspaceFileKey(item)])
      );
      map = keyBy(pre, (item) => workspaceFileKey(item));
    }
    return pre;
  }, []);
  return list;
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
  return (
    file.resourceKey ||
    file.url ||
    file.downloadUrl ||
    file.name ||
    ""
  );
}
