import { buildDeepSearchExtendMarkdown, resolveDeepSearchStage } from "@/utils/deepSearch";
import { buildSubAgentMarkdown, isAgentDispatchTask } from "@/utils/chat/subagent";
import {
  resolveTaskResultMap,
  resolveTaskToolResultText,
} from "@/utils/chat/toolCalls";
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

  // 工具入参（Structured data）暂不展示，仅保留调用摘要。
  return contentBlocks.join("\n\n");
}

export const resolveMarkdownContent = (taskItem?: PanelItemType) => {
  let markDownContent = "";

  if (!taskItem) {
    return markDownContent;
  }

  const { messageType } = taskItem;
  const resultMap = resolveTaskResultMap(taskItem);
  const toolResultText = resolveTaskToolResultText(taskItem);

  switch (messageType) {
    case "tool_result":
      if (isAgentDispatchTask(taskItem as unknown as CHAT.Task)) {
        markDownContent = buildSubAgentMarkdown(taskItem as unknown as CHAT.Task);
      } else {
        markDownContent = toolResultText;
      }
      break;
    case "llm_reasoning":
      markDownContent =
        (taskItem as { reasoningContent?: string }).reasoningContent ||
        taskItem.toolThought ||
        "";
      break;
    case "tool_thought":
      // 助手过程回复，不是思考
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
