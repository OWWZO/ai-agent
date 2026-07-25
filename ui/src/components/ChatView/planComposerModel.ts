/**
 * 从当前会话 taskList / chat 中推导「输入框上方」要展示的计划。
 * 优先 ExitPlanMode 的 planContent，其次 PlanSolve 结构化 stages。
 */

export type ComposerPlanSource = "plan_approval" | "structured_plan" | "plan_mode";

export type ComposerPlanModel = {
  source: ComposerPlanSource;
  title: string;
  planContent: string;
  planFilePath?: string;
  approvalId?: string;
  status?: string;
  /** 结构化步骤（可选） */
  steps?: string[];
  stepStatus?: string[];
};

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null
    ? (value as Record<string, unknown>)
    : {};
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
      nested.planFilePath || resultMap.planFilePath || toolAny.planFilePath || ""
    ),
    status: String(nested.status || resultMap.status || toolAny.status || "pending"),
  };
}

function flattenTasks(chat?: CHAT.ChatItem, taskList?: CHAT.Task[]): CHAT.Task[] {
  if (taskList && taskList.length > 0) {
    return taskList;
  }
  if (!chat?.tasks?.length) {
    return [];
  }
  const flat: CHAT.Task[] = [];
  for (const group of chat.tasks) {
    if (Array.isArray(group)) {
      flat.push(...group);
    }
  }
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

export function findLatestPlanModeEntered(
  chat?: CHAT.ChatItem,
  taskList?: CHAT.Task[]
): CHAT.Task | undefined {
  const tasks = flattenTasks(chat, taskList);
  for (let i = tasks.length - 1; i >= 0; i--) {
    if (tasks[i]?.messageType === "plan_mode_entered") {
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

  const approvalTask = findLatestPlanApproval(chat, taskList);
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
      };
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

  const entered = findLatestPlanModeEntered(chat, taskList);
  if (entered) {
    const resultMap = asRecord(entered.resultMap);
    const nested = asRecord(resultMap.resultMap);
    const toolAny = entered as unknown as Record<string, unknown>;
    const planFilePath = String(
      nested.planFilePath || resultMap.planFilePath || toolAny.planFilePath || ""
    );
    return {
      source: "plan_mode",
      title: "Plan Mode",
      planContent: "正在规划中… 计划写好后将显示在这里。",
      planFilePath: planFilePath || undefined,
      status: "planning",
    };
  }

  return null;
}
