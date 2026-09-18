import api from "./index";
import { resolveServiceBaseUrl } from "@/utils/origin";
import { getDeviceId } from "@/services/agentConversation";

const customHost = resolveServiceBaseUrl(SERVICE_BASE_URL);

export const AGENT_QUERY_SUBMIT_URL = `${customHost}/web/api/v1/gpt/queryAgentStreamIncr`;

export function buildAgentSessionStreamUrl(params: {
  sessionId: string;
  lastEventSeq?: number;
}): string {
  const search = new URLSearchParams();
  search.set("lastEventSeq", String(params.lastEventSeq || 0));
  return `${customHost}/api/agent/session/${encodeURIComponent(params.sessionId)}/stream?${search.toString()}`;
}

export type AgentQuerySubmitResult = {
  accepted: boolean;
  sessionId: string;
  requestId: string;
};

export class AgentQuerySubmitError extends Error {
  status: number;
  concurrent: boolean;

  constructor(message: string, status: number, concurrent = false) {
    super(message);
    this.name = "AgentQuerySubmitError";
    this.status = status;
    this.concurrent = concurrent;
  }
}

export async function submitAcceptedCommand(
  url: string,
  body: unknown
): Promise<AgentQuerySubmitResult> {
  const response = await fetch(url, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "X-Device-Id": getDeviceId(),
    },
    body: JSON.stringify(body),
  });
  const payload = (await response.json().catch(() => null)) as {
    code?: string;
    info?: string;
    data?: AgentQuerySubmitResult;
  } | null;
  if (response.status === 409) {
    throw new AgentQuerySubmitError(
      payload?.info || "已有任务在进行中，请等待完成或先停止后再试",
      409,
      true
    );
  }
  if (!response.ok) {
    throw new AgentQuerySubmitError(payload?.info || `请求失败，状态码: ${response.status}`, response.status);
  }
  if (!payload?.data?.accepted) {
    throw new AgentQuerySubmitError(payload?.info || "请求未被接受", response.status);
  }
  return payload.data;
}

export async function submitAgentQuery(body: unknown): Promise<AgentQuerySubmitResult> {
  return submitAcceptedCommand(AGENT_QUERY_SUBMIT_URL, body);
}

export const agentRunApi = {
  stop: (payload: { sessionId?: string; requestId: string }) =>
    api.post<Record<string, unknown>>("/api/agent/run/stop", payload) as unknown as Promise<
      Record<string, unknown>
    >,
  inject: (payload: { sessionId?: string; requestId: string; text: string }) =>
    api.post<Record<string, unknown>>("/api/agent/run/inject", payload) as unknown as Promise<
      Record<string, unknown>
    >,
};
