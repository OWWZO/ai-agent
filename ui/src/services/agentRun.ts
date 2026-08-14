import api from "./index";
import { resolveServiceBaseUrl } from "@/utils/origin";

const customHost = resolveServiceBaseUrl(SERVICE_BASE_URL);

export const AGENT_RUN_FOLLOW_SSE_URL = `${customHost}/api/agent/run/follow`;

export const agentRunApi = {
  stop: (payload: { sessionId?: string; requestId: string }) =>
    api.post<Record<string, unknown>>("/api/agent/run/stop", payload) as unknown as Promise<
      Record<string, unknown>
    >,
  /** 向进行中的 run 注入用户指导（控制面，不开新 SSE） */
  inject: (payload: { sessionId?: string; requestId: string; text: string }) =>
    api.post<Record<string, unknown>>("/api/agent/run/inject", payload) as unknown as Promise<
      Record<string, unknown>
    >,
};
