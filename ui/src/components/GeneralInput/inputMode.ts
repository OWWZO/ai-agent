import { toRequestOutputStyle } from "@/utils/constants";

type InputModeKey = "quick" | "think" | "research";

export function buildSubmitPayload(params: {
  question: string;
  visibleMode: InputModeKey;
  isDataAgent: boolean;
  currentProductType?: string;
  uploadedFiles: CHAT.TFile[];
  chatRole: CHAT.ConversationRole | null;
}) {
  const outputStyle = params.isDataAgent
    ? "dataAgent"
    : params.visibleMode === "quick"
      ? "chat"
      : toRequestOutputStyle(params.currentProductType);

  return {
    message: params.question.trim(),
    ...(outputStyle ? { outputStyle } : {}),
    deepThink:
      outputStyle !== "chat" && outputStyle !== "dataAgent"
        ? params.visibleMode === "research"
        : false,
    files: params.uploadedFiles.length > 0 ? params.uploadedFiles : undefined,
    aiAgentId: outputStyle === "chat" ? params.chatRole?.agentId : undefined,
  };
}
