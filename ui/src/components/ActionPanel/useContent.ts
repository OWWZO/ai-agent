import { buildDeepSearchExtendMarkdown, resolveDeepSearchStage } from "@/utils/deepSearch";
import { buildSubAgentMarkdown, isAgentDispatchTask } from "@/utils/chat/subagent";
import { PanelItemType } from "./type";

function buildToolCallMarkdown(resultMap?: PanelItemType["resultMap"]) {
  if (!resultMap) {
    return "";
  }

  const contentBlocks: string[] = [];
  const summary = typeof resultMap.summary === "string" ? resultMap.summary.trim() : "";
  if (summary) {
    contentBlocks.push(summary);
  } else if (resultMap.toolName) {
    contentBlocks.push(`正在调用 \`${resultMap.toolName}\``);
  }

  const input = resultMap.input || resultMap.toolParam;
  if (input && typeof input === "object") {
    contentBlocks.push(`\`\`\`json\n${JSON.stringify(input, null, 2)}\n\`\`\``);
  }

  return contentBlocks.join("\n\n");
}

export const resolveMarkdownContent = (taskItem?: PanelItemType) => {
  let markDownContent = "";

  if (!taskItem) {
    return markDownContent;
  }

  const { messageType, toolResult, resultMap } = taskItem;

  switch (messageType) {
    case "tool_result":
      if (isAgentDispatchTask(taskItem as unknown as CHAT.Task)) {
        markDownContent = buildSubAgentMarkdown(taskItem as unknown as CHAT.Task);
      } else {
        markDownContent = toolResult?.toolResult || "";
      }
      break;
    case "tool_thought":
      // 兜底支持思考内容，避免异常状态下工作区出现“有标题但无内容”的空白面板。
      markDownContent = taskItem.toolThought || "";
      break;
    case "tool_call":
      if (isAgentDispatchTask(taskItem as unknown as CHAT.Task)) {
        markDownContent = buildSubAgentMarkdown(taskItem as unknown as CHAT.Task);
      } else {
        markDownContent = buildToolCallMarkdown(resultMap);
      }
      break;
    case "code":
      // 细粒度过程区（任务/思考/代码/执行输出）落在 codeOutput；仅有 code 时兜底为 python 代码块
      if (resultMap?.codeOutput) {
        markDownContent = resultMap.codeOutput;
      } else if (resultMap?.code) {
        markDownContent = `\`\`\`python\n${resultMap.code}\n\`\`\``;
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
