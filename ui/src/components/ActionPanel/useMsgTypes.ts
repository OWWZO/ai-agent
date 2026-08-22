import { isHTML, isValidJSON } from "@/utils";
import { isAgentDispatchTask } from "@/utils/chat/subagent";
import {
  buildDeepSearchResultItems,
  resolveDeepSearchStage,
} from "@/utils/deepSearch";
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

export const getSearchList = (taskItem?: PanelItemType) => {
  if (!taskItem) {
    return [];
  }
  const { messageType, resultMap } = taskItem;

  const toolName = taskItem.toolResult?.toolName;
  if (messageType === 'tool_result') {
    if (toolName === 'internal_search' || toolName === 'web_search') {
      const toolResult = taskItem.toolResult?.toolResult;
      let tool: any = {};
      try {
        tool = JSON.parse(toolResult || "{}");
      } catch {
        tool = {};
      }
      const list = tool?.data || tool || [];
      return isValidJSON(toolResult) && list
        ? list?.map((item: MESSAGE.ToolResultDataType) => ({
          name: item.pageName || item.name,
          pageContent: item.pageContent || item.page_content,
          url: item.sourceUrl || item.source_url
        }))
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
    const { messageType, toolResult, resultMap } = taskItem;
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
    } else if (messageType === 'tool_result' && toolResult?.toolName === 'code_interpreter' && toolResult.toolResult) {
      isHtml = isHTML(toolResult.toolResult);
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
        !!toolResult?.toolResult &&
        isValidJSON(toolResult.toolResult) &&
        !isAgentDispatchTask(taskItem as unknown as CHAT.Task),
      isHtml,
      searchList,
      usePpt
    };
  }, [searchList, taskItem]);
};
