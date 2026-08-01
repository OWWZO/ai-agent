type InputModeKey = "quick" | "think" | "research";

/**
 * 组装发送载荷。
 * 输出格式（html/docs/ppt/table）已下线：标准任务不再透传 outputStyle，
 * 仅 chat / dataAgent 保留协议字段。
 */
export function buildSubmitPayload(params: {
  question: string;
  visibleMode: InputModeKey;
  isDataAgent: boolean;
  currentProductType?: string;
  uploadedFiles: CHAT.TFile[];
  chatRole: CHAT.ConversationRole | null;
}) {
  if (params.isDataAgent) {
    return {
      message: params.question.trim(),
      outputStyle: "dataAgent" as const,
      deepThink: false,
      files: params.uploadedFiles.length > 0 ? params.uploadedFiles : undefined,
    };
  }

  if (params.visibleMode === "quick") {
    return {
      message: params.question.trim(),
      outputStyle: "chat" as const,
      deepThink: false,
      files: params.uploadedFiles.length > 0 ? params.uploadedFiles : undefined,
      aiAgentId: params.chatRole?.agentId,
    };
  }

  return {
    message: params.question.trim(),
    deepThink: params.visibleMode === "research",
    files: params.uploadedFiles.length > 0 ? params.uploadedFiles : undefined,
  };
}
