export function isAnsweredAskUserTask(tool: CHAT.Task): boolean {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || resultMap) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  const askStatus = String(
    nested.status || resultMap.status || toolAny.status || "pending"
  ).trim().toLowerCase();
  return (
    askStatus === "answered" ||
    askStatus === "resuming" ||
    askStatus === "resume_pending" ||
    askStatus === "success" ||
    askStatus === "completed" ||
    Boolean(resultMap.isFinal && askStatus !== "pending") ||
    Boolean(nested.answers || resultMap.answers || toolAny.answers)
  );
}
