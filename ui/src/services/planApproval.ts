import api from "./index";
import { resolveServiceBaseUrl } from "@/utils/origin";

const customHost = resolveServiceBaseUrl(SERVICE_BASE_URL);

export const PLAN_APPROVAL_RESUME_SSE_URL = `${customHost}/api/agent/plan-approval/resume`;

export const PLAN_APPROVAL_RESUME_EVENT = "reactor-plan-approval-resume";

export type PlanApprovePayload = {
  approvalId: string;
  editedPlanContent?: string;
  feedback?: string;
};

export type PlanRejectPayload = {
  approvalId: string;
  feedback?: string;
};

export type PlanApprovalDecideResult = {
  approvalId?: string;
  accepted?: boolean;
  idempotent?: boolean;
  resumeRequestId?: string;
  sessionId?: string;
  status?: string;
  decision?: string;
  message?: string;
};

export type PlanApprovalResumeEventDetail = {
  resumeRequestId: string;
  sessionId?: string;
  approvalId?: string;
};

export const planApprovalApi = {
  approve: (payload: PlanApprovePayload) =>
    api.post<PlanApprovalDecideResult>("/api/agent/plan-approval/approve", payload) as unknown as Promise<PlanApprovalDecideResult>,

  reject: (payload: PlanRejectPayload) =>
    api.post<PlanApprovalDecideResult>("/api/agent/plan-approval/reject", payload) as unknown as Promise<PlanApprovalDecideResult>,

  pending: (sessionId: string) =>
    api.get<Record<string, unknown>[]>("/api/agent/plan-approval/pending", {
      sessionId,
    }) as unknown as Promise<Record<string, unknown>[]>,

  cancel: (approvalId: string) =>
    api.post<Record<string, unknown>>("/api/agent/plan-approval/cancel", {
      approvalId,
    }) as unknown as Promise<Record<string, unknown>>,
};

export function dispatchPlanApprovalResume(detail: PlanApprovalResumeEventDetail) {
  if (typeof window === "undefined" || !detail?.resumeRequestId) {
    return;
  }
  window.dispatchEvent(new CustomEvent(PLAN_APPROVAL_RESUME_EVENT, { detail }));
}
