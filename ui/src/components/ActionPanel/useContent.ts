import { PanelItemType } from "./type";

const useContent =  (taskItem?: PanelItemType) => {
  let markDownContent = '';

  // let fileUrl = '';
  if (!taskItem) {
    return {
      markDownContent,
      // fileUrl
    };
  }

  const { messageType, toolResult, resultMap } = taskItem;

  // const [fileInfo] = resultMap.fileInfo ?? [];

  switch (messageType) {
    case 'tool_result':
      markDownContent = toolResult?.toolResult || '';
      break;
    case 'tool_thought':
      // 兜底支持思考内容，避免异常状态下工作区出现“有标题但无内容”的空白面板。
      markDownContent = taskItem.toolThought || '';
      break;
    case 'code':
      if (resultMap?.code || (resultMap?.codeOutput && resultMap?.isFinal)) {
        const text = resultMap?.code || resultMap?.codeOutput;
        markDownContent = `\`\`\`python\n${text}\n\`\`\``;
      }
      break;
    case 'markdown':
    case 'html':
      markDownContent = resultMap?.codeOutput || '';
      break;
    case 'data_analysis':
      markDownContent = resultMap?.codeOutput || '';
      break;
    case 'deep_search':
    case 'report':
      markDownContent = resultMap.answer || '';
      break;
    // case 'data_analysis':
    //   markDownContent = resultMap?.codeOutput || '';
    //   break;
    // case 'file':
    //   fileUrl = fileInfo.domainUrl;
    //   break;
  }
  return {
    markDownContent,
    // fileUrl
  };
};

export default useContent;
