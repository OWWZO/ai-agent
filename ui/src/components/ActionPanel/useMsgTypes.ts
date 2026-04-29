import { isHTML, isValidJSON } from "@/utils";
import { buildDeepSearchResultItems } from "@/utils/deepSearch";
import { useMemo } from "react";
import { PanelItemType, SearchListItem } from "./type";
import { getPrimaryTaskFile, isImageFileLike } from "@/utils/taskArtifacts";

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
  if (messageType === 'deep_search' && resultMap.messageType === 'search') {
    return buildDeepSearchResultItems(resultMap?.searchResult?.docs) as SearchListItem[];
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
    const isPptFile = normalizedFileName.endsWith('.ppt')
      || normalizedFileName.endsWith('.pptx');
    const useExcel = !!primaryFile && (normalizedFileName.includes('.csv') || normalizedFileName.includes('.xlsx'));

    let isHtml = false;
    if (messageType === 'code' && resultMap.codeOutput) {
      isHtml = isHTML(resultMap.codeOutput);
    } else if (messageType === 'tool_result' && toolResult?.toolName === 'code_interpreter' && toolResult.toolResult) {
      isHtml = isHTML(toolResult.toolResult);
    }
    const useHtml = messageType === 'html' || (!!primaryFile && isHtmlFile);
    const usePpt = messageType === 'ppt' || (!!primaryFile && isPptFile);
    const useImage =
      isImageFile &&
      (
        messageType === 'file' ||
        (messageType === 'tool_result' && toolResult?.toolName === 'image_generation_tool')
      );
    const useFile =
      !!primaryFile &&
      !useImage &&
      !useExcel &&
      !useHtml &&
      !usePpt;
    const useCode = messageType === 'code' && !useFile;

    return {
      useBrowser: messageType === 'browser',
      useCode,
      useHtml,
      useImage,
      useExcel,
      useFile,
      useJSON: messageType === 'tool_result' && toolResult?.toolResult && isValidJSON(toolResult.toolResult),
      isHtml,
      searchList,
      usePpt
    };
  }, [searchList, taskItem]);
};
