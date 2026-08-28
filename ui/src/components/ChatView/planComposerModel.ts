/**
 * 从当前会话 taskList / chat 中推导「输入框上方」要展示的计划。
 * 优先 ExitPlanMode 的 planContent，其次 PlanSolve 结构化 stages。
 */

export type ComposerPlanSource = "plan_approval" | "structured_plan";

export type ComposerPlanModel = {
  source: ComposerPlanSource;
  title: string;
  planContent: string;
  planFilePath?: string;
  approvalId?: string;
  status?: string;
  approved?: boolean;
  feedback?: string;
  /** 结构化步骤（可选） */
  steps?: string[];
  stepStatus?: string[];
};

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null
    ? (value as Record<string, unknown>)
    : {};
}

/** 缺 status 时：已 finish/isFinal 视为 decided，避免续跑后回退审批 UI */
function resolvePlanApprovalStatus(
  tool: CHAT.Task,
  nested: Record<string, unknown>,
  resultMap: Record<string, unknown>,
  toolAny: Record<string, unknown>
): string {
  const raw = String(
    nested.status || resultMap.status || toolAny.status || ""
  )
    .trim()
    .toLowerCase();
  if (raw) {
    return raw;
  }
  if (
    tool.finish ||
    tool.isFinal ||
    resultMap.isFinal === true ||
    nested.isFinal === true
  ) {
    return "decided";
  }
  return "pending";
}

export function pickPlanApprovalFields(tool: CHAT.Task) {
  const resultMap = asRecord(tool.resultMap);
  const nested = asRecord(resultMap.resultMap);
  const toolAny = tool as unknown as Record<string, unknown>;
  return {
    approvalId: String(
      nested.approvalId || resultMap.approvalId || toolAny.approvalId || tool.messageId || ""
    ),
    planContent: String(
      nested.planContent || resultMap.planContent || toolAny.planContent || ""
    ),
    planFilePath: String(
      nested.planFilePath ||
        nested.planPath ||
        nested.path ||
        resultMap.planFilePath ||
        resultMap.planPath ||
        toolAny.planFilePath ||
        ""
    ),
    status: resolvePlanApprovalStatus(tool, nested, resultMap, toolAny),
    approved:
      typeof nested.approved === "boolean"
        ? nested.approved
        : typeof resultMap.approved === "boolean"
          ? resultMap.approved
          : undefined,
    feedback: String(nested.feedback || resultMap.feedback || ""),
  };
}

function isPendingPlanApproval(task?: CHAT.Task): boolean {
  if (!task || task.messageType !== "plan_approval") {
    return false;
  }
  return pickPlanApprovalFields(task).status === "pending";
}

function findLatestPendingPlanApproval(
  chat?: CHAT.ChatItem,
  taskList?: CHAT.Task[]
): CHAT.Task | undefined {
  const tasks = flattenTasks(chat, taskList);
  let fallback: CHAT.Task | undefined;
  for (let i = tasks.length - 1; i >= 0; i -= 1) {
    const task = tasks[i];
    if (!isPendingPlanApproval(task)) {
      continue;
    }
    if (pickPlanApprovalFields(task).planContent.trim()) {
      return task;
    }
    fallback ||= task;
  }
  return fallback;
}

function flattenTasks(chat?: CHAT.ChatItem, taskList?: CHAT.Task[]): CHAT.Task[] {
  const flat: CHAT.Task[] = [];
  const approvalIndexes = new Map<string, number>();
  const push = (task?: CHAT.Task) => {
    if (!task) return;
    if (task.messageType === "plan_approval") {
      const fields = pickPlanApprovalFields(task);
      if (fields.approvalId) {
        const previousIndex = approvalIndexes.get(fields.approvalId);
        if (previousIndex != null) {
          const previous = flat[previousIndex];
          if (
            previous &&
            pickPlanApprovalFields(previous).status === "pending" &&
            fields.status !== "pending"
          ) {
            flat[previousIndex] = task;
          }
          return;
        }
        approvalIndexes.set(fields.approvalId, flat.length);
      }
    }
    flat.push(task);
  };

  for (const group of chat?.multiAgent?.tasks || []) {
    for (const task of group || []) push(task as CHAT.Task);
  }
  for (const group of chat?.tasks || []) {
    for (const container of group || []) {
      const children = (container as CHAT.Task).children;
      if (Array.isArray(children) && children.length) {
        for (const child of children) push(child);
      } else {
        push(container as CHAT.Task);
      }
    }
  }
  for (const task of taskList || []) push(task);
  return flat;
}

/**
 * 从后往前找最新 plan_approval（有正文的优先）。
 */
export function findLatestPlanApproval(
  chat?: CHAT.ChatItem,
  taskList?: CHAT.Task[]
): CHAT.Task | undefined {
  const tasks = flattenTasks(chat, taskList);
  for (let i = tasks.length - 1; i >= 0; i--) {
    const task = tasks[i];
    if (task?.messageType !== "plan_approval") {
      continue;
    }
    const { planContent } = pickPlanApprovalFields(task);
    if (planContent.trim()) {
      return task;
    }
  }
  // 无正文也返回最后一个 plan_approval（可能还在写）
  for (let i = tasks.length - 1; i >= 0; i--) {
    if (tasks[i]?.messageType === "plan_approval") {
      return tasks[i];
    }
  }
  return undefined;
}

export function buildComposerPlanModel(params: {
  chat?: CHAT.ChatItem;
  taskList?: CHAT.Task[];
  structuredPlan?: CHAT.Plan;
}): ComposerPlanModel | null {
  const { chat, taskList, structuredPlan } = params;

  const latestApprovalTask = findLatestPlanApproval(chat, taskList);
  const approvalTask = findLatestPendingPlanApproval(chat, taskList);
  if (approvalTask) {
    const fields = pickPlanApprovalFields(approvalTask);
    if (fields.planContent.trim() || fields.approvalId) {
      return {
        source: "plan_approval",
        title:
          fields.status === "approved"
            ? "已批准的计划"
            : fields.status === "rejected"
              ? "待修订的计划"
              : "实现计划",
        planContent: fields.planContent,
        planFilePath: fields.planFilePath || undefined,
        approvalId: fields.approvalId || undefined,
        status: fields.status,
        approved: fields.approved,
        feedback: fields.feedback || undefined,
      };
    }
  }

  // 历史中的已决策 approval 只在时间线保留只读记录，不能回到底部再次伪装成审批。
  // 已批准计划没有结构化进度时隐藏旧条，避免历史记录再次显示过期计划。
  if (latestApprovalTask) {
    const latestFields = pickPlanApprovalFields(latestApprovalTask);
    const approvedAndSettled =
      latestFields.status !== "pending" && latestFields.approved !== false;
    if (
      approvedAndSettled &&
      !structuredPlan?.stages?.length &&
      !structuredPlan?.steps?.length
    ) {
      return null;
    }
  }

  if (structuredPlan?.stages?.length || structuredPlan?.steps?.length) {
    const steps = structuredPlan.steps?.length
      ? structuredPlan.steps
      : structuredPlan.stages || [];
    const lines = steps.map((step, index) => {
      const status = structuredPlan.stepStatus?.[index] || "not_started";
      const mark =
        status === "completed" ? "[x]" : status === "in_progress" ? "[~]" : "[ ]";
      return `${mark} ${step}`;
    });
    return {
      source: "structured_plan",
      title: structuredPlan.title || "研究路线",
      planContent: lines.join("\n"),
      steps: structuredPlan.steps,
      stepStatus: structuredPlan.stepStatus as string[] | undefined,
    };
  }

  return null;
}
