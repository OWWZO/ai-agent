import type { PanelItemType } from "../ActionPanel";
import {
  resolveDeepSearchStage,
  resolveDeepSearchTitle,
  shouldRenderDeepSearchWorkspace,
} from "@/utils/deepSearch";
import { getStableTaskIdentity } from "@/utils/chat";

export interface PreviewRendererFlags {
  useFile?: boolean;
  useHtml?: boolean;
  useExcel?: boolean;
  useImage?: boolean;
}

export function filterPreviewTaskList(taskList?: PanelItemType[]) {
  return (taskList || []).filter(
    (item) =>
      !["task_summary", "result"].includes(item.messageType) &&
      (
        item.messageType !== "deep_search" ||
        shouldRenderDeepSearchWorkspace(item.resultMap?.messageType)
      )
  );
}

export function resolvePreviewTaskSelection(params: {
  defaultTaskItem?: CHAT.Task;
  taskList: PanelItemType[];
  activeTaskIndex?: number;
}) {
  const { defaultTaskItem, taskList, activeTaskIndex } = params;
  let taskItem =
    typeof activeTaskIndex === "number"
      ? taskList[activeTaskIndex] || defaultTaskItem
      : defaultTaskItem;

  if (!taskItem) {
    taskItem = taskList[taskList.length - 1];
  }

  const realActiveTaskIndex = taskList.findIndex((item) => item.id === taskItem?.id);
  return {
    taskItem,
    realActiveTaskIndex: realActiveTaskIndex >= 0 ? realActiveTaskIndex : 0,
    taskLength: taskList.length,
  };
}

export function resolvePreviewTitle(
  taskItem?: CHAT.Task | PanelItemType,
  primaryFile?: CHAT.TFile
) {
  if (!taskItem) {
    return "";
  }

  const { messageType, resultMap } = taskItem;
  if (messageType === "tool_result") {
    if (
      taskItem.toolResult?.toolName === "image_generation_tool" &&
      primaryFile?.name
    ) {
      return primaryFile.name;
    }
    return taskItem.toolResult?.toolName || "工具执行";
  }

  if (
    ["file", "html", "markdown", "code", "ppt", "data_analysis"].includes(
      messageType
    )
  ) {
    return primaryFile?.name || messageType;
  }

  if (messageType === "deep_search") {
    const stage = resolveDeepSearchStage(resultMap?.messageType);
    const titleQueries =
      stage === "report" ? resultMap?.query : resultMap?.searchResult?.query;
    return resolveDeepSearchTitle(stage, titleQueries);
  }

  return messageType;
}

export function resolvePreviewLeadingIcon(
  taskItem?: CHAT.Task | PanelItemType
) {
  if (taskItem?.messageType !== "deep_search") {
    return undefined;
  }

  const stage = resolveDeepSearchStage(taskItem.resultMap?.messageType);
  return stage === "extend" || stage === "search" ? "search" : undefined;
}

export function resolvePreviewCanPreview(
  flags: PreviewRendererFlags | undefined,
  artifactMissing: boolean
) {
  return !artifactMissing && Boolean(
    flags?.useFile || flags?.useHtml || flags?.useExcel || flags?.useImage
  );
}

export function resolvePreviewTaskRenderKey(
  taskItem?: CHAT.Task | PanelItemType
) {
  return getStableTaskIdentity(taskItem) || "empty";
}
