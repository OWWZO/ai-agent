import { isHTML, isValidJSON } from "@/utils";
import { useMemo } from "react";
import { PanelItemType, SearchListItem } from "./type";

export const getSearchList = (taskItem?: PanelItemType) => {
  if (!taskItem) {
    return [];
  }
  const { messageType, resultMap } = taskItem;

  const toolName = taskItem.toolResult?.toolName;
  if (messageType === 'tool_result') {
    if (toolName === 'internal_search' || toolName === 'web_search') {
      const toolResult = taskItem.toolResult?.toolResult;
      const tool = JSON.parse(toolResult || "{}");
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
    const list = resultMap?.searchResult?.docs || [];
    return list.map(item => {
      const ele = Array.isArray(item) ? item[0] : item;
      return {
        name: ele.title,
        pageContent: ele.content,
        url: ele.link
      };
    });
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
    const [fileInfo] = taskItem.resultMap?.fileInfo || [];
    const { messageType, toolResult, resultMap } = taskItem;
    const { fileName } = fileInfo || {};

    let isHtml = false;
    if (messageType === 'code' && resultMap.codeOutput) {
      isHtml = isHTML(resultMap.codeOutput);
    } else if (messageType === 'tool_result' && toolResult?.toolName === 'code_interpreter' && toolResult.toolResult) {
      isHtml = isHTML(toolResult.toolResult);
    }
    return {
      useBrowser: messageType === 'browser',
      useCode: messageType === 'code',
      useHtml: messageType === 'html',
      useExcel: messageType === 'file' && (fileName.includes('.csv') || fileName.includes('.xlsx')),
      useFile: taskItem.messageType === 'file' && !(fileName.includes('.csv') || fileName.includes('.xlsx')),
      useJSON: messageType === 'tool_result' && toolResult?.toolResult && isValidJSON(toolResult.toolResult),
      isHtml,
      searchList,
      usePpt: messageType === 'ppt'
    };
  }, [searchList, taskItem]);
};