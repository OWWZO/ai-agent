import api from "./index";

export const agentRunApi = {
  stop: (payload: { sessionId?: string; requestId: string }) =>
    api.post<Record<string, unknown>>("/api/agent/run/stop", payload) as unknown as Promise<
      Record<string, unknown>
    >,
};
