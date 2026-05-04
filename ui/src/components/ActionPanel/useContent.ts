import { buildDeepSearchExtendMarkdown, resolveDeepSearchStage } from "@/utils/deepSearch";
import { PanelItemType } from "./type";

export const resolveMarkdownContent = (taskItem?: PanelItemType) => {
  let markDownContent = "";

  if (!taskItem) {
    return markDownContent;
  }

  const { messageType, toolResult, resultMap } = taskItem;

  switch (messageType) {
    case "tool_result":
      markDownContent = toolResult?.toolResult || "";
      break;
    case "tool_thought":
      // 兜底支持思考内容，避免异常状态下工作区出现“有标题但无内容”的空白面板。
      markDownContent = taskItem.toolThought || "";
      break;
    case "code":
      if (resultMap?.code || (resultMap?.codeOutput && resultMap?.isFinal)) {
        const text = resultMap?.code || resultMap?.codeOutput;
        markDownContent = `\`\`\`python\n${text}\n\`\`\``;
      }
      break;
    case "markdown":
    case "html":
      markDownContent = resultMap?.codeOutput || "";
      break;
    case "data_analysis":
      markDownContent = resultMap?.codeOutput || "";
      break;
    case "deep_search":
    case "report":
      // 查询分解阶段还没有搜索结果文档，用 Markdown 先把待检索子查询展示出来。
      if (
        messageType === "deep_search" &&
        resolveDeepSearchStage(resultMap?.messageType) === "extend" &&
        !resultMap?.answer
      ) {
        markDownContent = buildDeepSearchExtendMarkdown(resultMap?.searchResult?.query);
        break;
      }
      markDownContent = resultMap.answer || "";
      break;
  }

  return markDownContent;
};

const useContent =  (taskItem?: PanelItemType) => {
  const markDownContent = resolveMarkdownContent(taskItem);

  // let fileUrl = '';
  return {
    markDownContent,
    // fileUrl
  };
};

export default useContent;
