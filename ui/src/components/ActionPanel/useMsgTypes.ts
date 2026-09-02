import { isHTML, isValidJSON } from "@/utils";
import { isAgentDispatchTask } from "@/utils/chat/subagent";
import {
  buildDeepSearchResultItems,
  resolveDeepSearchStage,
} from "@/utils/deepSearch";
import {
  resolveTaskResultMap,
  resolveTaskToolResult,
  resolveTaskToolResultText,
} from "@/utils/chat/toolCalls";
import { useMemo } from "react";
import { PanelItemType, SearchListItem } from "./type";
import {
  getPrimaryTaskFile,
  isDocxFileLike,
  isExcelFileLike,
  isImageFileLike,
  isLegacyDocFileLike,
  isPdfFileLike,
  isPptFileLike,
} from "@/utils/taskArtifacts";
import { normalizeToolName } from "@/components/Dialogue/tools/toolMeta";

export const getSearchList = (taskItem?: PanelItemType) => {
  if (!taskItem) {
    return [];
  }
  const { messageType } = taskItem;
  const resultMap = resolveTaskResultMap(taskItem);

  const toolResult = resolveTaskToolResult(taskItem);
  const toolName = normalizeToolName(String(
    toolResult?.toolName ||
    (typeof resultMap.toolName === "string" ? resultMap.toolName : "")
  ));
  if (messageType === 'tool_result') {
    if (toolName === 'search') {
      const resultText = resolveTaskToolResultText(taskItem);
      let tool: any = {};
      try {
        tool = JSON.parse(resultText || "{}");
      } catch {
        tool = {};
      }
      const list =
        [tool?.data, tool?.hits, tool?.results, tool].find((value) =>
          Array.isArray(value)
        ) || [];
      return isValidJSON(resultText) && list.length
        ? list.map((item) => {
          const row =
            item && typeof item === "object"
              ? (item as Record<string, unknown>)
              : {};
          return {
            name: String(row.pageName || row.name || row.title || ""),
            pageContent: String(
              row.pageContent ||
                row.page_content ||
                row.snippet ||
                row.content ||
                ""
            ),
            url: String(
              row.sourceUrl ||
                row.source_url ||
                row.url ||
                row.link ||
                ""
            ),
          };
        })
        : [];
    }
    return [];
  }
  if (messageType === 'knowledge') {
    const list = resultMap?.refList || [];
    return list.map(item => ({
      name: item.name,
      pageContent: item.pageContent,
      url: item.sourceUrl
    }));
  }
  if (messageType === "deep_search") {
    const stage = resolveDeepSearchStage(resultMap?.messageType);
    if (stage === "search" || stage === "chapter_summary") {
      return buildDeepSearchResultItems(resultMap?.searchResult?.docs) as SearchListItem[];
    }
  }
  return [];
};

export const useMsgTypes = (taskItem?: PanelItemType) => {

  const searchList = useMemo<SearchListItem[]>(() => {
    return getSearchList(taskItem);
  }, [taskItem]);

  return useMemo(() => {
    if (!taskItem) {
      return;
    }
    const { messageType } = taskItem;
    const resultMap = resolveTaskResultMap(taskItem);
    const toolResult = resolveTaskToolResult(taskItem);
    const toolResultText = resolveTaskToolResultText(taskItem);
    const rawToolName = String(toolResult?.toolName || "").toLowerCase();
    const primaryFile = getPrimaryTaskFile(taskItem);
    const fileName = primaryFile?.name || '';
    const isImageFile = isImageFileLike(primaryFile);
    const normalizedFileName = fileName.toLowerCase();
    const normalizedMimeType = (primaryFile?.mimeType || '').toLowerCase();
    const isHtmlFile = normalizedFileName.endsWith('.html')
      || normalizedFileName.endsWith('.htm')
      || normalizedMimeType.includes('text/html');
    const useExcel = !!primaryFile && isExcelFileLike(primaryFile);
    const usePdf = !!primaryFile && isPdfFileLike(primaryFile);
    const useDocx = !!primaryFile && isDocxFileLike(primaryFile);
    const useLegacyDoc = !!primaryFile && isLegacyDocFileLike(primaryFile);
    const useWord = useDocx || useLegacyDoc;

    let isHtml = false;
    if (messageType === 'code' && resultMap.codeOutput) {
      isHtml = isHTML(resultMap.codeOutput);
    } else if (messageType === 'tool_result' && rawToolName === 'code_interpreter' && toolResultText) {
      isHtml = isHTML(toolResultText);
    }
    const useHtml = messageType === 'html' || (!!primaryFile && isHtmlFile);
    const useGenUi = messageType === 'ui_tree';
    const usePpt = messageType === 'ppt' || (!!primaryFile && isPptFileLike(primaryFile));
    // 只要主文件是图片就走图片预览（图表/工具产物常见为 tool_result + .png）
    const useImage = isImageFile;
    const useFile =
      !!primaryFile &&
      !useImage &&
      !useExcel &&
      !useHtml &&
      !usePpt &&
      !usePdf &&
      !useWord;
    const useCode = messageType === 'code' && !useFile;

    return {
      useBrowser: messageType === 'browser',
      useCode,
      useHtml,
      useGenUi,
      useImage,
      useExcel,
      usePdf,
      useDocx,
      useLegacyDoc,
      useWord,
      useFile,
      // Agent 观察值可能是 JSON，但仍走 SubAgent markdown，不能当成空白 Structured data。
      useJSON:
        messageType === "tool_result" &&
        !!toolResultText &&
        isValidJSON(toolResultText) &&
        !isAgentDispatchTask(taskItem as unknown as CHAT.Task),
      isHtml,
      searchList,
      usePpt
    };
  }, [searchList, taskItem]);
};
