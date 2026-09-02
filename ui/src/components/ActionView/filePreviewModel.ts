import type { PanelItemType } from "../ActionPanel";
import {
  resolveDeepSearchStage,
  resolveDeepSearchTitle,
  shouldRenderDeepSearchWorkspace,
} from "@/utils/deepSearch";
import { getStableTaskIdentity } from "@/utils/chat";
import {
  resolveTaskResultMap,
  resolveTaskToolResult,
} from "@/utils/chat/toolCalls";

export interface PreviewRendererFlags {
  useFile?: boolean;
  useHtml?: boolean;
  useExcel?: boolean;
  useImage?: boolean;
  usePdf?: boolean;
  useDocx?: boolean;
  useLegacyDoc?: boolean;
  useWord?: boolean;
}

export function filterPreviewTaskList(taskList?: PanelItemType[]) {
  // 摘要/最终结果不是普通产物 tab；深搜只保留允许展示 workspace 的阶段。
  return (taskList || []).filter(
    (item) =>
      !["task_summary", "result"].includes(item.messageType) &&
      (
        item.messageType !== "deep_search" ||
        shouldRenderDeepSearchWorkspace(resolveTaskResultMap(item).messageType)
      )
  );
}

export function resolvePreviewTaskSelection(params: {
  defaultTaskItem?: CHAT.Task;
  taskList: PanelItemType[];
  activeTaskIndex?: number;
}) {
  // 优先使用外部 active index，其次默认任务，最后回退到列表末项，兼容历史回放和实时任务。
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

  const { messageType } = taskItem;
  // 标题按工具结果、文件产物、深搜阶段依次解析，避免把内部 messageType 直接暴露给用户。
  if (messageType === "tool_result") {
    const toolResult = resolveTaskToolResult(taskItem);
    if (
      toolResult?.toolName === "image_generation_tool" &&
      primaryFile?.name
    ) {
      return primaryFile.name;
    }
    return toolResult?.toolName || "工具执行";
  }

  if (
    ["file", "html", "markdown", "code", "ppt", "data_analysis"].includes(
      messageType
    )
  ) {
    return primaryFile?.name || messageType;
  }

  if (messageType === "deep_search") {
    const resolvedResultMap = resolveTaskResultMap(taskItem);
    const stage = resolveDeepSearchStage(resolvedResultMap.messageType);
    const titleQueries =
      stage === "report"
        ? resolvedResultMap.query
        : resolvedResultMap.chapterTitle || resolvedResultMap.searchResult?.query;
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

  const stage = resolveDeepSearchStage(resolveTaskResultMap(taskItem).messageType);
  return stage === "extend" ||
    stage === "search" ||
    stage === "chapter_summary"
    ? "search"
    : undefined;
}

export function resolvePreviewCanPreview(
  flags: PreviewRendererFlags | undefined,
  artifactMissing: boolean
) {
  // 缺少 artifact 时即使 message type 支持，也不能打开空预览面板。
  return !artifactMissing && Boolean(
    flags?.useFile ||
      flags?.useHtml ||
      flags?.useExcel ||
      flags?.useImage ||
      flags?.usePdf ||
      flags?.useDocx ||
      flags?.useLegacyDoc ||
      flags?.useWord
  );
}

export function resolvePreviewTaskRenderKey(
  taskItem?: CHAT.Task | PanelItemType
) {
  return getStableTaskIdentity(taskItem) || "empty";
}
