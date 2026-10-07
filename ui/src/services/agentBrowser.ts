import api from "./index";

export type AgentBrowserStatus = {
  exists: boolean;
  agentSessionId?: string;
  lastUsedAt?: string;
  reconstructed: boolean;
};

export type AgentBrowserLiveView = {
  browserLiveViewUrl: string;
  agentSessionId: string;
  reconstructed: boolean;
};

export const agentBrowserApi = {
  status: () => api.get<AgentBrowserStatus>("/api/agent/agent-browser/status") as unknown as Promise<AgentBrowserStatus>,
  ensure: () => api.post<AgentBrowserLiveView>("/api/agent/agent-browser/ensure") as unknown as Promise<AgentBrowserLiveView>,
  reset: () => api.delete<void>("/api/agent/agent-browser/session") as unknown as Promise<void>,
};
