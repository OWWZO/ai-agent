import api from "./index";

// 计划审批 API：审批、拒绝和取消都以 approvalId 关联同一待处理计划。
export type PlanApprovePayload = {
  approvalId: string;
  editedPlanContent?: string;
  feedback?: string;
};

export type PlanRejectPayload = {
  approvalId: string;
  feedback?: string;
};

export const planApprovalApi = {
  approve: (payload: PlanApprovePayload) =>
    api.post<Record<string, unknown>>("/api/agent/plan-approval/approve", payload) as unknown as Promise<
      Record<string, unknown>
    >,

  reject: (payload: PlanRejectPayload) =>
    api.post<Record<string, unknown>>("/api/agent/plan-approval/reject", payload) as unknown as Promise<
      Record<string, unknown>
    >,

  pending: (sessionId: string) =>
    api.get<Record<string, unknown>[]>("/api/agent/plan-approval/pending", {
      sessionId,
    }) as unknown as Promise<Record<string, unknown>[]>,

  cancel: (approvalId: string) =>
    api.post<Record<string, unknown>>("/api/agent/plan-approval/cancel", {
      approvalId,
    }) as unknown as Promise<Record<string, unknown>>,
};
