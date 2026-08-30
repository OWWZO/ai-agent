type InputModeKey = "think" | "research";

/**
 * 组装发送载荷。
 * 标准 Agent 只透传 deepThink；dataAgent 仍由独立问数入口处理。
 */
export function buildSubmitPayload(params: {
  question: string;
  visibleMode: InputModeKey;
  isDataAgent: boolean;
  uploadedFiles: CHAT.TFile[];
  model?: string;
  thinking?: boolean;
  thinkingEffort?: string | null;
}) {
  const model = params.model?.trim() || undefined;
  const thinking = params.thinking;
  const thinkingEffort = params.thinking
    ? params.thinkingEffort || "medium"
    : undefined;

  if (params.isDataAgent) {
    return {
      message: params.question.trim(),
      outputStyle: "dataAgent" as const,
      deepThink: false,
      files: params.uploadedFiles.length > 0 ? params.uploadedFiles : undefined,
      model,
      thinking,
      thinkingEffort,
    };
  }

  return {
    message: params.question.trim(),
    deepThink: params.visibleMode === "research",
    files: params.uploadedFiles.length > 0 ? params.uploadedFiles : undefined,
    model,
    thinking,
    thinkingEffort,
  };
}
