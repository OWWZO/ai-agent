import api from "./index";
import { resolveServiceBaseUrl } from "@/utils/origin";

const customHost = resolveServiceBaseUrl(SERVICE_BASE_URL);

export const ASK_USER_RESUME_SSE_URL = `${customHost}/api/agent/ask-user/resume`;

export type AskUserAnswerPayload = {
  questionId: string;
  answers: Record<string, string>;
};

export type AskUserAnswerResult = {
  questionId?: string;
  accepted?: boolean;
  idempotent?: boolean;
  resumeRequestId?: string;
  sessionId?: string;
  status?: string;
  message?: string;
};

export const ASK_USER_RESUME_EVENT = "reactor-ask-user-resume";

export type AskUserResumeEventDetail = {
  resumeRequestId: string;
  sessionId?: string;
  questionId?: string;
  answers?: Record<string, string>;
};

export const askUserApi = {
  answer: (payload: AskUserAnswerPayload) =>
    api.post<AskUserAnswerResult>("/api/agent/ask-user/answer", payload) as unknown as Promise<AskUserAnswerResult>,

  pending: (sessionId: string) =>
    api.get<Record<string, unknown>[]>("/api/agent/ask-user/pending", {
      sessionId,
    }) as unknown as Promise<Record<string, unknown>[]>,

  cancel: (questionId: string) =>
    api.post<Record<string, unknown>>("/api/agent/ask-user/cancel", {
      questionId,
    }) as unknown as Promise<Record<string, unknown>>,
};

export function dispatchAskUserResume(detail: AskUserResumeEventDetail) {
  if (typeof window === "undefined" || !detail?.resumeRequestId) {
    return;
  }
  window.dispatchEvent(new CustomEvent(ASK_USER_RESUME_EVENT, { detail }));
}
