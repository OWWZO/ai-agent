import api from "./index";

export type AskUserAnswerPayload = {
  questionId: string;
  answers: Record<string, string>;
};

export const askUserApi = {
  answer: (payload: AskUserAnswerPayload) =>
    api.post<Record<string, unknown>>("/api/agent/ask-user/answer", payload) as unknown as Promise<
      Record<string, unknown>
    >,

  pending: (sessionId: string) =>
    api.get<Record<string, unknown>[]>("/api/agent/ask-user/pending", {
      sessionId,
    }) as unknown as Promise<Record<string, unknown>[]>,

  cancel: (questionId: string) =>
    api.post<Record<string, unknown>>("/api/agent/ask-user/cancel", {
      questionId,
    }) as unknown as Promise<Record<string, unknown>>,
};
