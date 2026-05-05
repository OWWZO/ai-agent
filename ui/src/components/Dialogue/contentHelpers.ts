/**
 * 时间线任务总结和最终结论共用同一套文本回退顺序，
 * 这样可以避免不同展示区各自兜底时出现文案不一致。
 */
export function resolveTaskSummaryText(task?: CHAT.Task) {
  if (!task) {
    return "";
  }

  const taskRecord = task as unknown as Record<string, unknown>;
  const resultMapRecord = (task.resultMap || {}) as Record<string, unknown>;

  return (
    (typeof resultMapRecord.taskSummary === "string" ? resultMapRecord.taskSummary : "") ||
    (typeof taskRecord.taskSummary === "string" ? taskRecord.taskSummary : "") ||
    task.result ||
    (typeof resultMapRecord.result === "string" ? resultMapRecord.result : "") ||
    ""
  );
}
